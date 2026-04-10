package com.example.ragbackend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

public interface DashScopeEmbeddingService extends EmbeddingModel {

    List<Embedding> embedSegments(List<TextSegment> segments);
}
