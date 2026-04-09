package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.ChatSessionEntity;

import java.util.List;

public interface ChatSessionService extends IService<ChatSessionEntity> {
    Long createSession(Long userId);
    List<ChatSessionEntity> getUserSessions(Long userId);
    boolean deleteSession(Long sessionId, Long userId);
}