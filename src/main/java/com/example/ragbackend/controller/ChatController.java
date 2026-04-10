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


    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody ChatRequestDTO dto) {
        log.info("用户请求会话: {}, 内容: {}", dto.getSessionId(), dto.getMessage());

        if (dto.getSessionId() == null) {
            return Result.error("会话ID不能为空");
        }

        // 每次消息发送都更新会话活跃时间
        touchSession(dto.getSessionId());

        // 从 Security 上下文获取身份标识
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        // 通过工厂动态创建带上下文的实例
        ChatService chatService = chatServiceFactory.create(userId, isAdmin, dto.getRagMode());

        try {
            // 1. 核心调用
            String aiResponse = chatService.chat(dto.getSessionId(), dto.getMessage());

            // 2. 额外小功能：如果是该会话的第一条消息，自动更新会话标题
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

        // 每次流式消息开始发送也更新会话活跃时间
        touchSession(dto.getSessionId());

        // 设置较长超时时间（例如 60 分钟），Ollama慢的话也不会轻易断开
        SseEmitter emitter = new SseEmitter(3600000L);

        if (dto.getSessionId() == null) {
            try {
                // 错误数据统一为 JSON 格式
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("code", 400);
                errorBody.put("message", "会话ID不能为空");
                emitter.send(SseEmitter.event().name("error").data(errorBody));
                emitter.complete();
            } catch (Exception e) {}
            return emitter;
        }

        // 超时或断开回调整理
        emitter.onCompletion(() -> log.info("SSE Connection Completed for Stream."));
        emitter.onTimeout(() -> {
            log.warn("SSE Connection Timeout for Stream.");
            emitter.complete();
        });
        emitter.onError((e) -> log.error("SSE Connection Error for Stream.", e));

        // 从 Security 上下文获取身份标识
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        // 通过工厂动态创建带上下文的实例
        ChatService chatService = chatServiceFactory.create(userId, isAdmin, dto.getRagMode());

        TokenStream tokenStream = chatService.chatStream(dto.getSessionId(), dto.getMessage());

        tokenStream.onNext(token -> {
            try {
                // 将 token 包装为 JSON 以应对换行符导致的断帧
                emitter.send(SseEmitter.event().data(Collections.singletonMap("text", token)));
            } catch (Exception e) {
                log.error("Token 发送失败", e);
                emitter.completeWithError(e);
            }
        }).onComplete(response -> {
            try {
                emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
                emitter.complete();

                // 会话新标题小功能
                updateSessionTitleIfNew(dto.getSessionId(), dto.getMessage());
            } catch (Exception e) {
                log.error("完成流传输异常", e);
                emitter.completeWithError(e);
            }
        }).onError(error -> {
            log.error("流式输出异常", error);
            try {
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("code", 500);
                errorBody.put("message", "生成回复时出错: " + error.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errorBody));
                emitter.completeWithError(error);
            } catch (Exception e) {}
        }).start();

        return emitter;
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
