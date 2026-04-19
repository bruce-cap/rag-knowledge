package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.ChatMessageEntity;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {
    List<ChatMessageEntity> listSessionMessages(Long sessionId, Long userId);

    void deleteOwnedMessage(Long messageId, Long userId);

    void editOwnedMessage(Long messageId, Long userId, String content);
}
