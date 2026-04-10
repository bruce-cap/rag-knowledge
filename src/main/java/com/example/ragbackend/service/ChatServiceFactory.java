package com.example.ragbackend.service;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * ChatService 动态工厂
 * 负责根据请求上下文（用户ID、权限、是否开启RAG）动态构建带状态的 ChatService 实例。
 * 彻底移除对 ThreadLocal (RagContextHolder) 和 SecurityContextHolder 的隐式依赖。
 */
@Service
@Slf4j
public class ChatServiceFactory {

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Autowired
    private OllamaStreamingChatModel ollamaStreamingChatModel;

    @Value("${rag.config.min-score:0.6}")
    private Double minScore;

    @Value("${rag.config.max-results:5}")
    private Integer maxResults;

    private static final String RAG_TEMPLATE = 
            "请根据以下参考资料回答问题。如果资料中没有相关信息，请直接回答不知。\n\n参考资料：\n%s\n\n--- 原始问题 ---\n%s";

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private DashScopeEmbeddingService dashScopeEmbeddingService;

    /**
     * 创建一个配置好的 ChatService 实例
     *
     * @param userId  当前用户ID
     * @param isAdmin 是否为管理员
     * @param ragMode 是否开启RAG增强
     * @return 针对该请求定制的 ChatService 代理实例
     */
    public ChatService create(Long userId, boolean isAdmin, boolean ragMode, ChatMemory chatMemory) {
        // 1. 基础 AI 服务配置
        AiServices<ChatService> builder = AiServices.builder(ChatService.class)
                .chatLanguageModel(ollamaChatModel)
                .streamingChatLanguageModel(ollamaStreamingChatModel)
                .chatMemory(chatMemory);

        log.info("ChatServiceFactory: userId={}, isAdmin={}, ragMode={}, chatMemory={}", userId, isAdmin, ragMode,
                chatMemory);

        // 3. 动态 RAG 配置：仅在启用时挂载检索器，并直接绑定用户过滤逻辑
        if (ragMode) {
            Filter filter = null;
            if (!isAdmin) {
                // 普通用户：只能检索属于自己的文档或公开文档
                filter = metadataKey("user_id").isEqualTo(String.valueOf(userId))
                        .or(metadataKey("is_public").isEqualTo("true"));
            }
            // 管理员不设置 filter，默认检索全库

            log.info("RAG 模式已启用 - 正在为用户 {} 构建自定义检索增强器 (isAdmin={})", userId, isAdmin);

            // 1. 检索器：从全局配置读取阈值，避免硬编码
            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(dashScopeEmbeddingService)
                    .filter(filter)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            // 2. 自定义注入器：引用静态模版，减少分配开销
            ContentInjector contentInjector = (contents, query) -> {
                log.info("RAG 检索完毕 - 查询: [{}], 召回片段: {}", query.text(), contents.size());

                String joinedContext = contents.stream()
                        .map(Content::textSegment)
                        .map(TextSegment::text)
                        .collect(java.util.stream.Collectors.joining("\n\n"));
                
                String augmentedPrompt = String.format(RAG_TEMPLATE, joinedContext, query.text());
                log.info("RAG 提示词拼接完成，上下文总长度: {}", joinedContext.length());
                
                return UserMessage.from(augmentedPrompt);
            };

            // 3. 构建增强器
            dev.langchain4j.rag.RetrievalAugmentor retrievalAugmentor = dev.langchain4j.rag.DefaultRetrievalAugmentor
                    .builder()
                    .contentRetriever(contentRetriever)
                    .contentInjector(contentInjector)
                    .build();

            builder.retrievalAugmentor(retrievalAugmentor);
        }

        return builder.build();
    }
}
