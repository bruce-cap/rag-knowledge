package com.example.ragbackend.service;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatServiceFactory {

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Autowired
    private OllamaStreamingChatModel ollamaStreamingChatModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private DashScopeEmbeddingService dashScopeEmbeddingService;

    @Autowired
    private SpaceAccessService spaceAccessService;

    @Value("${rag.config.min-score:0.6}")
    private Double minScore;

    @Value("${rag.config.max-results:5}")
    private Integer maxResults;

    private static final String RAG_TEMPLATE =
            "请根据以下参考资料回答问题。如果资料中没有相关信息，请直接回答不知道。\n\n参考资料：\n%s\n\n--- 原始问题 ---\n%s";

    public ChatService create(Long userId, boolean isAdmin, boolean ragMode, ChatMemory chatMemory) {
        AiServices<ChatService> builder = AiServices.builder(ChatService.class)
                .chatLanguageModel(ollamaChatModel)
                .streamingChatLanguageModel(ollamaStreamingChatModel)
                .chatMemory(chatMemory);

        log.info("ChatServiceFactory userId={}, isAdmin={}, ragMode={}", userId, isAdmin, ragMode);

        if (ragMode) {
            Filter filter = spaceAccessService.buildSearchFilter(userId, isAdmin, null, null);

            ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(dashScopeEmbeddingService)
                    .filter(filter)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            ContentInjector contentInjector = (contents, query) -> {
                log.info("RAG retrieval finished, query=[{}], hits={}", query.text(), contents.size());

                String joinedContext = contents.stream()
                        .map(Content::textSegment)
                        .map(TextSegment::text)
                        .collect(java.util.stream.Collectors.joining("\n\n"));

                String augmentedPrompt = String.format(RAG_TEMPLATE, joinedContext, query.text());
                log.info("RAG prompt assembled, contextLength={}", joinedContext.length());
                return UserMessage.from(augmentedPrompt);
            };

            dev.langchain4j.rag.RetrievalAugmentor retrievalAugmentor =
                    dev.langchain4j.rag.DefaultRetrievalAugmentor.builder()
                            .contentRetriever(contentRetriever)
                            .contentInjector(contentInjector)
                            .build();

            builder.retrievalAugmentor(retrievalAugmentor);
        }

        return builder.build();
    }
}
