package com.example.ragbackend.config;

import com.example.ragbackend.service.ChatService;
import com.example.ragbackend.service.PersistentChatMemoryStore;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Autowired
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    /**
     * 手动创建并配置 ChatService Bean
     * 这样可以确保 ChatMemory 和 Model 被百分之百注入
     */
    @Bean
    public ChatService chatService() {
        // 1. 定义记忆供应逻辑
        ChatMemoryProvider chatMemoryProvider = sessionId -> MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(10)
                .chatMemoryStore(persistentChatMemoryStore)
                .build();

        // 2. 使用 AiServices 建造者强行绑定
        return AiServices.builder(ChatService.class)
                .chatLanguageModel(ollamaChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
}