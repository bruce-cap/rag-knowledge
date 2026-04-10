package com.example.ragbackend.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

public interface DashScopeEmbeddingService {

    List<Embedding> embedSegments(List<TextSegment> segments);
}
