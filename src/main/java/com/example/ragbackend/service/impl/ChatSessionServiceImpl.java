package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionEntity> implements ChatSessionService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public Long createSession(Long userId) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUserId(userId);
        session.setTitle("新的会话");
        this.save(session);
//        ChatMessageEntity init = new ChatMessageEntity();
//        init.setSessionId(session.getId());
//        init.setRole("assistant");
//        init.setContent("你好，我是你的知识库助手");
//        chatMessageMapper.insert(init);
        return session.getId();
    }

    @Override
    public List<ChatSessionEntity> getUserSessions(Long userId) {
        return this.list(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getUserId, userId)
                .orderByDesc(ChatSessionEntity::getUpdateTime));
    }

    @Override
    public boolean deleteSession(Long sessionId, Long userId) {
        // 增加权限校验：确保只能删除自己的会话
        return this.remove(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, sessionId)
                .eq(ChatSessionEntity::getUserId, userId));
    }
}

