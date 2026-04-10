package com.example.ragbackend.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * ChatService 动态工厂
 * 负责根据请求上下文（用户ID、权限、是否开启RAG）动态构建带状态的 ChatService 实例。
 * 彻底移除对 ThreadLocal (RagContextHolder) 和 SecurityContextHolder 的隐式依赖。
 */
@Service
public class ChatServiceFactory {

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Autowired
    private OllamaStreamingChatModel ollamaStreamingChatModel;

    @Autowired
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private DashScopeEmbeddingService dashScopeEmbeddingService;

    /**
     * 创建一个配置好的 ChatService 实例
     *
     * @param userId   当前用户ID
     * @param isAdmin  是否为管理员
     * @param ragMode  是否开启RAG增强
     * @return 针对该请求定制的 ChatService 代理实例
     */
    public ChatService create(Long userId, boolean isAdmin, boolean ragMode) {
        // 1. 定义记忆供应逻辑 (显式闭包捕获注入的存储组件)
        ChatMemoryProvider chatMemoryProvider = sessionId -> MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(10)
                .chatMemoryStore(persistentChatMemoryStore)
                .build();

        // 2. 基础 AI 服务配置
        AiServices<ChatService> builder = AiServices.builder(ChatService.class)
                .chatLanguageModel(ollamaChatModel)
                .streamingChatLanguageModel(ollamaStreamingChatModel)
                .chatMemoryProvider(chatMemoryProvider);

        // 3. 动态 RAG 配置：仅在启用时挂载检索器，并直接绑定用户过滤逻辑
        if (ragMode) {
            Filter filter = null;
            if (!isAdmin) {
                // 普通用户：只能检索属于自己的文档或公开文档
                filter = metadataKey("user_id").isEqualTo(String.valueOf(userId))
                        .or(metadataKey("is_public").isEqualTo("true"));
            }
            // 管理员不设置 filter，默认检索全库

            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(dashScopeEmbeddingService)
                    .filter(filter)
                    .maxResults(5) // 取最相关的 5 段
                    .minScore(0.7)
                    .build();
            
            builder.contentRetriever(contentRetriever);
        }

        return builder.build();
    }
}
