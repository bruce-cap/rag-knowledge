package com.example.ragbackend.config;

import com.example.ragbackend.service.ChatService;
import com.example.ragbackend.service.PersistentChatMemoryStore;

import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Autowired
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Bean
    public OllamaChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Value("${langchain4j.ollama.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.ollama.chat-model.temperature}")
    private Double temperature;

    @Bean
    public OllamaStreamingChatModel ollamaStreamingChatModel() {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Value("${chroma.url}")
    private String chromaUrl;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return ChromaEmbeddingStore.builder()
                .baseUrl(chromaUrl)
                .collectionName("rag_knowledge")
                .build();
    }

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
                .chatLanguageModel(ollamaChatModel())
                .streamingChatLanguageModel(ollamaStreamingChatModel())
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
}
