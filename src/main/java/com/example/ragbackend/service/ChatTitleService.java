package com.example.ragbackend.service;

public interface ChatTitleService {

    void generateTitleAsync(Long sessionId, String userMessage, String aiMessage);
}
