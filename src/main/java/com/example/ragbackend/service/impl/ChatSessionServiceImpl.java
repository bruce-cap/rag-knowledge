package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.service.ChatSessionService;
import com.example.ragbackend.service.ChatTitleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionEntity> implements ChatSessionService {

    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatTitleService chatTitleService;

    @Override
    public Long createSession(Long userId) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUserId(userId);
        session.setTitle(DEFAULT_TITLE);
        session.setIsTitleManual(false);
        session.setIsPinned(false);
        session.setPinTime(null);
        save(session);
        return session.getId();
    }

    @Override
    public List<ChatSessionEntity> getUserSessions(Long userId) {
        return list(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getUserId, userId)
                .orderByDesc(ChatSessionEntity::getIsPinned)
                .orderByDesc(ChatSessionEntity::getPinTime)
                .orderByDesc(ChatSessionEntity::getUpdateTime));
    }

    @Override
    public boolean deleteSession(Long sessionId, Long userId) {
        return remove(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, sessionId)
                .eq(ChatSessionEntity::getUserId, userId));
    }

    @Override
    public ChatSessionEntity updateSessionTitle(Long sessionId, Long userId, String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException(400, "Title cannot be empty");
        }
        ChatSessionEntity session = getOwnedSession(sessionId, userId);
        session.setTitle(title.trim());
        session.setIsTitleManual(true);
        session.setUpdateTime(LocalDateTime.now());
        updateById(session);
        return session;
    }

    @Override
    public ChatSessionEntity pinSession(Long sessionId, Long userId) {
        ChatSessionEntity session = getOwnedSession(sessionId, userId);
        session.setIsPinned(true);
        session.setPinTime(LocalDateTime.now());
        updateById(session);
        return session;
    }

    @Override
    public ChatSessionEntity unpinSession(Long sessionId, Long userId) {
        ChatSessionEntity session = getOwnedSession(sessionId, userId);
        session.setIsPinned(false);
        session.setPinTime(null);
        updateById(session);
        return session;
    }

    @Override
    public String exportSessionMarkdown(Long sessionId, Long userId) {
        ChatSessionEntity session = getOwnedSession(sessionId, userId);
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getCreateTime));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(session.getTitle()).append("\n\n");
        markdown.append("导出时间：").append(LocalDateTime.now().format(EXPORT_TIME_FORMATTER)).append("\n\n");

        for (ChatMessageEntity message : messages) {
            markdown.append("## ").append(resolveMarkdownRole(message.getRole())).append("\n");
            markdown.append(message.getContent()).append("\n\n");
        }

        return markdown.toString();
    }

    @Override
    public void generateTitleIfNeeded(Long sessionId, String userMessage, String aiMessage) {
        chatTitleService.generateTitleAsync(sessionId, userMessage, aiMessage);
    }

    private ChatSessionEntity getOwnedSession(Long sessionId, Long userId) {
        ChatSessionEntity session = getOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, sessionId)
                .eq(ChatSessionEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(404, "Chat session not found");
        }
        return session;
    }

    private String resolveMarkdownRole(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "助手";
        }
        return "用户";
    }
}
