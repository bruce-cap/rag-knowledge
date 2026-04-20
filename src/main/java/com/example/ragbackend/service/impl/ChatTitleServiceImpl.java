package com.example.ragbackend.service.impl;

import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.service.ChatSessionService;
import com.example.ragbackend.service.ChatTitleService;
import com.example.ragbackend.service.TitleGenerationAiService;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatTitleServiceImpl implements ChatTitleService {

    private static final String PROMPT_TEMPLATE = """
            请根据以下首轮对话生成一个简洁的中文会话标题。
            要求：
            1. 只输出标题，不要解释
            2. 标题长度控制在8到20个中文字符
            3. 不要以“关于”“请问”“帮我”等口语开头
            4. 不要使用句号、问号、感叹号结尾
            5. 尽量概括主题，而不是照抄原句

            用户首条消息：
            %s

            助手首条回复：
            %s
            """;

    private final TitleGenerationAiService aiService;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    public ChatTitleServiceImpl(OllamaChatModel ollamaChatModel) {
        this.aiService = AiServices.builder(TitleGenerationAiService.class)
                .chatLanguageModel(ollamaChatModel)
                .build();
    }

    @Override
    @Async
    public void generateTitleAsync(Long sessionId, String userMessage, String aiMessage) {
        if (sessionId == null || userMessage == null || userMessage.isBlank() || aiMessage == null || aiMessage.isBlank()) {
            return;
        }

        ChatSessionEntity latestSession = chatSessionMapper.selectById(sessionId);
        if (latestSession == null
                || Boolean.TRUE.equals(latestSession.getIsTitleManual())
                || !ChatSessionService.DEFAULT_TITLE.equals(latestSession.getTitle())) {
            return;
        }

        try {
            String prompt = PROMPT_TEMPLATE.formatted(userMessage.trim(), aiMessage.trim());
            String title = sanitizeTitle(aiService.generateTitle(prompt));
            if (title == null || title.isBlank()) {
                return;
            }

            ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
            if (session == null
                    || Boolean.TRUE.equals(session.getIsTitleManual())
                    || !ChatSessionService.DEFAULT_TITLE.equals(session.getTitle())) {
                return;
            }

            session.setTitle(title);
            chatSessionMapper.updateById(session);
            log.info("Generated chat session title, sessionId={}, title={}", sessionId, title);
        } catch (Exception ex) {
            log.warn("Failed to generate chat title, sessionId={}, error={}", sessionId, ex.getMessage());
        }
    }

    private String sanitizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.replace("\n", " ").replace("\r", " ").trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        normalized = normalized.replaceAll("[。！？!?]+$", "");
        if (normalized.length() > 20) {
            normalized = normalized.substring(0, 20).trim();
        }
        return normalized;
    }
}
