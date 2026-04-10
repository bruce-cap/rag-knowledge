package com.example.ragbackend.config;

import com.example.ragbackend.service.PersistentChatMemoryStore;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Autowired
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Autowired
    private DashScopeEmbeddingService dashScopeEmbeddingService;

    @Bean
    public OllamaChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(java.time.Duration.ofMinutes(2))
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
                .timeout(java.time.Duration.ofMinutes(10))
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
}
