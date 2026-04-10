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
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.filter.Filter;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import com.example.ragbackend.utils.SecurityUtils;
import java.util.List;
import java.util.Collections;
import com.example.ragbackend.utils.RagContextHolder;
import com.example.ragbackend.service.DashScopeEmbeddingService;
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

        ContentRetriever dynamicRetriever = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                // 检查当前会话是否开启了 RAG 模式
                if (!RagContextHolder.isRagMode()) {
                    return Collections.emptyList();
                }
                Long userId = SecurityUtils.getCurrentUserId();

                // 核心逻辑升级：管理员可以检索全库，普通用户只能查“自己+公开”
                Filter filter = null;
                if (!SecurityUtils.isAdmin()) {
                    filter = metadataKey("user_id").isEqualTo(String.valueOf(userId))
                            .or(metadataKey("is_public").isEqualTo("true"));
                }

                EmbeddingStoreContentRetriever delegate = EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(embeddingStore())
                        .embeddingModel(dashScopeEmbeddingService)
                        .filter(filter)
                        .maxResults(5) // 取最相关的 5 段
                        .minScore(0.7)
                        .build();
                return delegate.retrieve(query);
            }
        };

        // 3. 使用 AiServices 建造者强行绑定
        return AiServices.builder(ChatService.class)
                .chatLanguageModel(ollamaChatModel())
                .streamingChatLanguageModel(ollamaStreamingChatModel())
                .chatMemoryProvider(chatMemoryProvider)
                .contentRetriever(dynamicRetriever)
                .build();
    }
}
