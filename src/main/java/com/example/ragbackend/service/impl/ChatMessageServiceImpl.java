package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity> implements ChatMessageService {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Override
    public List<ChatMessageEntity> listSessionMessages(Long sessionId, Long userId) {
        ensureSessionOwnership(sessionId, userId);
        return this.list(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getCreateTime));
    }

    @Override
    public void deleteOwnedMessage(Long messageId, Long userId) {
        ChatMessageEntity message = getRequiredMessage(messageId);
        ensureSessionOwnership(message.getSessionId(), userId);
        if (!this.removeById(messageId)) {
            throw new BusinessException("Failed to delete message");
        }
    }

    @Override
    public void editOwnedMessage(Long messageId, Long userId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(400, "Message content cannot be empty");
        }

        ChatMessageEntity message = getRequiredMessage(messageId);
        ensureSessionOwnership(message.getSessionId(), userId);
        message.setContent(content.trim());
        if (!this.updateById(message)) {
            throw new BusinessException("Failed to update message");
        }
    }

    private ChatMessageEntity getRequiredMessage(Long messageId) {
        ChatMessageEntity message = this.getById(messageId);
        if (message == null) {
            throw new BusinessException(404, "Message not found");
        }
        return message;
    }

    private void ensureSessionOwnership(Long sessionId, Long userId) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "Session not found");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(403, "You do not have permission to access this session");
        }
    }
}
