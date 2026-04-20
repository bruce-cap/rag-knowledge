package com.example.ragbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.model.dto.ChatRequestDTO;
import com.example.ragbackend.service.ChatService;
import com.example.ragbackend.service.ChatServiceFactory;
import com.example.ragbackend.service.ChatSessionService;
import com.example.ragbackend.service.SpaceAccessService;
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
import org.springframework.web.bind.annotation.*;
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

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private SpaceAccessService spaceAccessService;

    @Value("${rag.memory.max-tokens:2000}")
    private Integer maxTokens;

    @Value("${rag.memory.keep-min-messages:2}")
    private Integer keepMinMessages;

    @Deprecated
    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody ChatRequestDTO dto) {
        validateRequest(dto);
        Long userId = SecurityUtils.getCurrentUserId();
        ensureSessionOwnership(dto.getSessionId(), userId);
        validateRagScope(userId, dto);

        saveUserMessage(dto.getSessionId(), dto.getMessage());
        touchSession(dto.getSessionId());

        ChatMemory memory = prepareMemory(dto.getSessionId());
        boolean isAdmin = SecurityUtils.isAdmin();
        ChatService chatService = chatServiceFactory.create(
                userId,
                isAdmin,
                Boolean.TRUE.equals(dto.getRagMode()),
                dto.getSpaceId(),
                dto.getFolderId(),
                memory
        );

        try {
            String aiResponse = chatService.chat(dto.getMessage());
            saveAiMessage(dto.getSessionId(), aiResponse);
            chatSessionService.generateTitleIfNeeded(dto.getSessionId(), dto.getMessage(), aiResponse);
            return Result.success(aiResponse);
        } catch (Exception e) {
            log.error("Chat request failed, sessionId={}, error={}", dto.getSessionId(), e.getMessage(), e);
            return Result.error("AI is busy: " + e.getMessage());
        }
    }

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody ChatRequestDTO dto) {
        try {
            validateRequest(dto);
            Long userId = SecurityUtils.getCurrentUserId();
            ensureSessionOwnership(dto.getSessionId(), userId);
            validateRagScope(userId, dto);

            saveUserMessage(dto.getSessionId(), dto.getMessage());
            touchSession(dto.getSessionId());

            SseEmitter emitter = new SseEmitter(3600000L);
            emitter.onCompletion(() -> log.info("SSE completed, sessionId={}", dto.getSessionId()));
            emitter.onTimeout(() -> {
                log.warn("SSE timeout, sessionId={}", dto.getSessionId());
                emitter.complete();
            });
            emitter.onError(error -> log.error("SSE error, sessionId={}, error={}", dto.getSessionId(), error.getMessage(), error));

            ChatMemory memory = prepareMemory(dto.getSessionId());
            boolean isAdmin = SecurityUtils.isAdmin();
            ChatService chatService = chatServiceFactory.create(
                    userId,
                    isAdmin,
                    Boolean.TRUE.equals(dto.getRagMode()),
                    dto.getSpaceId(),
                    dto.getFolderId(),
                    memory
            );

            StringBuilder responseBuffer = new StringBuilder();
            TokenStream tokenStream = chatService.chatStream(dto.getMessage());
            tokenStream.onNext(token -> {
                responseBuffer.append(token);
                try {
                    emitter.send(SseEmitter.event().data(Collections.singletonMap("text", token)));
                } catch (Exception e) {
                    log.error("Failed to send SSE token, sessionId={}", dto.getSessionId(), e);
                }
            }).onComplete(response -> {
                try {
                    String finalResponse = response != null && response.content() != null
                            ? response.content().text()
                            : responseBuffer.toString();
                    if (finalResponse != null && !finalResponse.isBlank()) {
                        saveAiMessage(dto.getSessionId(), finalResponse);
                        chatSessionService.generateTitleIfNeeded(dto.getSessionId(), dto.getMessage(), finalResponse);
                    }
                    emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
                    emitter.complete();
                } catch (Exception e) {
                    log.error("Failed to complete SSE stream, sessionId={}", dto.getSessionId(), e);
                    emitter.complete();
                }
            }).onError(error -> {
                log.error("Streaming chat failed, sessionId={}, error={}", dto.getSessionId(), error.getMessage(), error);
                try {
                    emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    emitter.completeWithError(error);
                } catch (Exception ignored) {
                    emitter.complete();
                }
            }).start();

            return emitter;
        } catch (Exception ex) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }
    }

    private void validateRequest(ChatRequestDTO dto) {
        if (dto == null || dto.getSessionId() == null) {
            throw new BusinessException(400, "Session ID cannot be empty");
        }
        if (dto.getMessage() == null || dto.getMessage().trim().isEmpty()) {
            throw new BusinessException(400, "Message cannot be empty");
        }
        if (dto.getFolderId() != null && dto.getSpaceId() == null) {
            throw new BusinessException(400, "spaceId is required when folderId is specified");
        }
    }

    private void validateRagScope(Long userId, ChatRequestDTO dto) {
        if (!Boolean.TRUE.equals(dto.getRagMode()) || dto.getSpaceId() == null || SecurityUtils.isAdmin()) {
            return;
        }
        if (!spaceAccessService.canAccessSpace(userId, dto.getSpaceId())) {
            throw new BusinessException(403, "You do not have permission to search this space");
        }
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
            log.warn("Token window kept too few messages, fallback to message window, sessionId={}", sessionId);
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

    private void ensureSessionOwnership(Long sessionId, Long userId) {
        ChatSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(404, "Chat session does not belong to the current user");
        }
    }
}
