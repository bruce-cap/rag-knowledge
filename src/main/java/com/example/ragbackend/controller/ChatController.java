package com.example.ragbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.model.dto.ChatRequestDTO;
import com.example.ragbackend.service.ChatService;
import com.example.ragbackend.service.ChatServiceFactory;
import com.example.ragbackend.utils.SecurityUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_3_5_TURBO;

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


    //已弃用，现在改为使用stream流式输出

    /**
     * 注意：此方法已标记为弃用，建议使用流式接口 {@link #sendMessageStream(ChatRequestDTO)}
     *      * 以获得更好的用户体验。
     * @param dto
     * @return Result
     */
    @Deprecated
    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody ChatRequestDTO dto) {
        log.info("Chat request received, sessionId={}, ragMode={}", dto.getSessionId(), dto.getRagMode());

        if (dto.getSessionId() == null) {
            return Result.error("Session ID cannot be empty");
        }
        ensureSessionOwnership(dto.getSessionId());

        saveUserMessage(dto.getSessionId(), dto.getMessage());
        touchSession(dto.getSessionId());

        ChatMemory memory = prepareMemory(dto.getSessionId());
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        ChatService chatService = chatServiceFactory.create(userId, isAdmin, dto.getRagMode(), memory);

        try {
            String aiResponse = chatService.chat(dto.getMessage());
            saveAiMessage(dto.getSessionId(), aiResponse);
            updateSessionTitleIfNew(dto.getSessionId(), dto.getMessage());
            return Result.success(aiResponse);
        } catch (Exception e) {
            log.error("Chat request failed, sessionId={}", dto.getSessionId(), e);
            return Result.error("AI is busy: " + e.getMessage());
        }
    }

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody ChatRequestDTO dto) {
        log.info("Streaming chat request received, sessionId={}, ragMode={}", dto.getSessionId(), dto.getRagMode());

        if (dto.getSessionId() == null) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("Session ID cannot be empty"));
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }
        ensureSessionOwnership(dto.getSessionId());

        saveUserMessage(dto.getSessionId(), dto.getMessage());
        touchSession(dto.getSessionId());

        SseEmitter emitter = new SseEmitter(3600000L);
        emitter.onCompletion(() -> log.info("SSE connection completed for sessionId={}", dto.getSessionId()));
        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout for sessionId={}", dto.getSessionId());
            emitter.complete();
        });
        emitter.onError(error -> log.error("SSE connection error for sessionId={}", dto.getSessionId(), error));

        ChatMemory memory = prepareMemory(dto.getSessionId());
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        ChatService chatService = chatServiceFactory.create(userId, isAdmin, dto.getRagMode(), memory);

        TokenStream tokenStream = chatService.chatStream(dto.getMessage());
        tokenStream.onNext(token -> {
            try {
                emitter.send(SseEmitter.event().data(Collections.singletonMap("text", token)));
            } catch (Exception e) {
                log.error("Failed to send SSE token for sessionId={}", dto.getSessionId(), e);
            }
        }).onComplete(response -> {
            try {
                emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
                emitter.complete();

                if (response != null && response.content() != null) {
                    saveAiMessage(dto.getSessionId(), response.content().text());
                }
                updateSessionTitleIfNew(dto.getSessionId(), dto.getMessage());
            } catch (Exception e) {
                log.error("Failed to complete SSE stream for sessionId={}", dto.getSessionId(), e);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }).onError(error -> {
            log.error("Streaming chat failed for sessionId={}", dto.getSessionId(), error);
            try {
                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                emitter.completeWithError(error);
            } catch (Exception ignored) {
                try {
                    emitter.complete();
                } catch (Exception ignoredAgain) {
                }
            }
        }).start();

        return emitter;
    }

    private ChatMemory prepareMemory(Long sessionId) {
        ChatMemory memory = TokenWindowChatMemory.builder()
                .maxTokens(maxTokens, new OpenAiTokenizer(GPT_3_5_TURBO))
                .build();

        List<ChatMessageEntity> dbMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ChatMessageEntity::getCreateTime)
                        .last("LIMIT 30")
        );

        if (dbMessages == null || dbMessages.isEmpty()) {
            return memory;
        }

        Collections.reverse(dbMessages);
        for (ChatMessageEntity dbMsg : dbMessages) {
            if ("user".equalsIgnoreCase(dbMsg.getRole())) {
                memory.add(UserMessage.from(dbMsg.getContent()));
            } else if ("assistant".equalsIgnoreCase(dbMsg.getRole())) {
                memory.add(AiMessage.from(dbMsg.getContent()));
            }
        }

        List<ChatMessage> currentMessages = memory.messages();
        if (currentMessages.size() < keepMinMessages && dbMessages.size() >= keepMinMessages) {
            log.warn("Token window kept too few messages, falling back to message window. sessionId={}", sessionId);
            memory = MessageWindowChatMemory.withMaxMessages(keepMinMessages);
            for (int i = dbMessages.size() - keepMinMessages; i < dbMessages.size(); i++) {
                ChatMessageEntity dbMsg = dbMessages.get(i);
                if ("user".equalsIgnoreCase(dbMsg.getRole())) {
                    memory.add(UserMessage.from(dbMsg.getContent()));
                } else {
                    memory.add(AiMessage.from(dbMsg.getContent()));
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

    private void touchSession(Long sessionId) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private void updateSessionTitleIfNew(Long sessionId, String firstContent) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session != null && "新的会话".equals(session.getTitle())) {
            String newTitle = firstContent.length() > 15 ? firstContent.substring(0, 15) + "..." : firstContent;
            session.setTitle(newTitle);
            sessionMapper.updateById(session);
        }
    }

    private void ensureSessionOwnership(Long sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("Session does not belong to the current user");
        }
    }
}

