package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.ChatSessionEntity;

import java.util.List;

public interface ChatSessionService extends IService<ChatSessionEntity> {

    String DEFAULT_TITLE = "新的会话";

    Long createSession(Long userId);

    List<ChatSessionEntity> getUserSessions(Long userId);

    boolean deleteSession(Long sessionId, Long userId);

    ChatSessionEntity updateSessionTitle(Long sessionId, Long userId, String title);

    ChatSessionEntity pinSession(Long sessionId, Long userId);

    ChatSessionEntity unpinSession(Long sessionId, Long userId);

    String exportSessionMarkdown(Long sessionId, Long userId);

    void generateTitleIfNeeded(Long sessionId, String userMessage, String aiMessage);
}
