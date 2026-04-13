package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.model.dto.ChatRequestDTO;
import com.example.ragbackend.service.ChatService;
import com.example.ragbackend.service.ChatServiceFactory;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import dev.langchain4j.service.TokenStream;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatServiceFactory chatServiceFactory;

    @Autowired
    private ChatSessionMapper sessionMapper;

    @Autowired
    private ChatMessageMapper messageMapper;

    @Value("${rag.memory.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${rag.memory.keep-min-messages:2}")
    private Integer keepMinMessages;

	//添加弃用注解
	@Deprecated
    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody ChatRequestDTO dto) {
        log.info("用户请求会话: {}, 内容: {}", dto.getSessionId(), dto.getMessage());

        if (dto.getSessionId() == null) {
            return Result.error("会话ID不能为空");
        }

        // 1. 同步保存原始用户提问到数据库（此时无 RAG 污染）
        saveUserMessage(dto.getSessionId(), dto.getMessage());

        // 2. 更新会话活跃时间
        touchSession(dto.getSessionId());

        // 3. 构建该次请求专用的内存快照（加载历史记录）
        ChatMemory memory = prepareMemory(dto.getSessionId());

        // 4. 从 Security 上下文获取身份标识
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        // 5. 通过工厂创建实例（由于已手动 add 历史到 memory，Factory 直接绑定即可）
        ChatService chatService = chatServiceFactory.create(userId, isAdmin, dto.getRagMode(), memory);

        try {
            // 6. 核心调用
            String aiResponse = chatService.chat(dto.getMessage());

            // 7. 保存 AI 响应
            saveAiMessage(dto.getSessionId(), aiResponse);

            // 8. 如果是第一条消息更新标题
            updateSessionTitleIfNew(dto.getSessionId(), dto.getMessage());

            return Result.success(aiResponse);
        } catch (Exception e) {
            log.error("对话失败", e);
            return Result.error("AI 忙碌中: " + e.getMessage());
        }
    }

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody ChatRequestDTO dto) {
        log.info("SSE Stream 请求会话: {}, 内容: {}", dto.getSessionId(), dto.getMessage());

        if (dto.getSessionId() == null) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("会话ID不能为空"));
                emitter.complete();
            } catch (Exception e) {}
            return emitter;
        }

        // 1. 同步保存用户原始提问（确保消息不因异常丢失）
        saveUserMessage(dto.getSessionId(), dto.getMessage());
        touchSession(dto.getSessionId());

        SseEmitter emitter = new SseEmitter(3600000L);
        emitter.onCompletion(() -> log.info("SSE Connection Completed for Stream."));
        emitter.onTimeout(() -> {
            log.warn("SSE Connection Timeout for Stream.");
            emitter.complete();
        });
        emitter.onError((e) -> log.error("SSE Connection Error for Stream.", e));

        // 2. 准备内存与服务
        ChatMemory memory = prepareMemory(dto.getSessionId());
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        ChatService chatService = chatServiceFactory.create(userId, isAdmin, dto.getRagMode(), memory);

        // 3. 启动流
        TokenStream tokenStream = chatService.chatStream(dto.getMessage());

        tokenStream.onNext(token -> {
            try {
                emitter.send(SseEmitter.event().data(Collections.singletonMap("text", token)));
            } catch (Exception e) {
                log.error("SSE Token 发送失败: {}", e.getMessage());
            }
        }).onComplete(response -> {
            try {
                // 优先发送结束信号，降低前端感知延迟
                emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
                emitter.complete();
                
                // 异步/后台持久化 AI 响应，不阻塞 SSE 完成
                if (response != null && response.content() != null) {
                    saveAiMessage(dto.getSessionId(), response.content().text());
                }
                updateSessionTitleIfNew(dto.getSessionId(), dto.getMessage());
            } catch (Exception e) {
                log.error("SSE 完成回调处理异常: {}", e.getMessage());
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }).onError(error -> {
            log.error("AI 生成流发生错误: {}", error.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                emitter.completeWithError(error);
            } catch (Exception e) {
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }).start();

        return emitter;
    }

    /**
     * 智能加载历史记录并灌入 TokenWindowChatMemory
     * 逻辑：计重制 (Token) 代替计件制，并支持强制保底条数
     */
    private ChatMemory prepareMemory(Long sessionId) {
        // 1. 初始化 Token 驱动的内存容器
        ChatMemory memory = TokenWindowChatMemory.builder()
                .maxTokens(maxTokens, new OpenAiTokenizer(GPT_3_5_TURBO))
                .build();
        
        // 2. 扩大历史提取范围（提取最近 30 条，交给 TokenMemory 进行智能裁剪）
        List<ChatMessageEntity> dbMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ChatMessageEntity::getCreateTime)
                        .last("LIMIT 30")
        );

        if (dbMessages == null || dbMessages.isEmpty()) {
            return memory;
        }

        // 3. 将消息反放（因为是 DESC 取出的，需反转回 ASC 顺序喂给录音机）
        Collections.reverse(dbMessages);
        
        // 4. 重放历史
        for (ChatMessageEntity dbMsg : dbMessages) {
            if ("user".equalsIgnoreCase(dbMsg.getRole())) {
                memory.add(new UserMessage(dbMsg.getContent()));
            } else if ("assistant".equalsIgnoreCase(dbMsg.getRole())) {
                memory.add(new AiMessage(dbMsg.getContent()));
            }
        }

        // 5. 【保底机制】：如果 Token 限制太严导致条目过少，强制补回最后几条
        // 实际上 2000 Token 几乎不会发生这种情况，此处作为防御性逻辑
        List<ChatMessage> currentMessages = memory.messages();
        if (currentMessages.size() < keepMinMessages && dbMessages.size() >= keepMinMessages) {
            log.warn("检查到 Token 裁剪过于激进，正在执行保底策略. sessionId={}", sessionId);
            // 重新创建一个足够大的临时 Container 来容纳保底条目
            memory = MessageWindowChatMemory.withMaxMessages(keepMinMessages);
            for (int i = dbMessages.size() - keepMinMessages; i < dbMessages.size(); i++) {
                ChatMessageEntity m = dbMessages.get(i);
                if ("user".equalsIgnoreCase(m.getRole())) {
                    memory.add(new UserMessage(m.getContent()));
                } else {
                    memory.add(new AiMessage(m.getContent()));
                }
            }
        }
        
        return memory;
    }

    private void saveUserMessage(Long sessionId, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setContent(content);
        entity.setCreateTime(LocalDateTime.now());
        messageMapper.insert(entity);
    }

    private void saveAiMessage(Long sessionId, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setCreateTime(LocalDateTime.now());
        messageMapper.insert(entity);
    }

    /**
     * 更新会话的最后活跃时间，用于排序
     */
    private void touchSession(Long sessionId) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session != null) {
            // MyBatis-Plus 在 updateById 时会自动更新 update_time (如果有自动填充配置)
            // 如果没有配置，这里手动更新它
            session.setUpdateTime(java.time.LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private void updateSessionTitleIfNew(Long sessionId, String firstContent) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session != null && "新的对话".equals(session.getTitle())) {
            // 截取前10个字作为标题
            String newTitle = firstContent.length() > 15 ? firstContent.substring(0, 15) + "..." : firstContent;
            session.setTitle(newTitle);
            // touchSession 已经更过一次了，这里再次 update 也会更新 updateTime
            sessionMapper.updateById(session);
        }
    }





//    @PostMapping("/send")
//    public Result<ChatResponseVO> sendMessage(@RequestBody ChatRequestDTO request) {
//        log.info("收到聊天请求，内容为: {}", request);
//        try {
//            ChatResponseVO response = chatService.chat(request);
//            log.info("AI 响应成功: {}", response);
//            return Result.success(response);
//        } catch (Exception e) {
//            log.error("AI 响应失败，错误信息: ", e);
//            return Result.error("AI 忙碌中: " + e.getMessage());
//        }
//    }
}
