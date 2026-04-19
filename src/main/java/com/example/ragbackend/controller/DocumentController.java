package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.model.dto.SearchRequestDTO;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.utils.SecurityUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@RestController
@RequestMapping("/api/document")
@CrossOrigin
@Slf4j
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private DashScopeEmbeddingService dashScopeEmbeddingService;

    @PostMapping("/search")
    public Result<?> searchDocument(@RequestBody SearchRequestDTO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} requests vector search, query={}", userId, request.getQuery());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return Result.error("Search query cannot be empty");
        }

        Embedding queryEmbedding = dashScopeEmbeddingService.embedSegments(List.of(TextSegment.from(request.getQuery()))).get(0);

        Filter filter = null;
        if (!SecurityUtils.isAdmin()) {
            filter = metadataKey("user_id").isEqualTo(String.valueOf(userId))
                    .or(metadataKey("is_public").isEqualTo("true"));
        }

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(request.getLimit())
                .minScore(request.getMinScore())
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        List<Map<String, Object>> results = searchResult.matches().stream().map(match -> {
            Map<String, Object> map = new HashMap<>();
            map.put("text", match.embedded().text());
            map.put("score", match.score());
            map.put("documentId", match.embedded().metadata().getString("document_id"));
            map.put("spaceId", match.embedded().metadata().getString("space_id"));
            map.put("folderId", match.embedded().metadata().getString("folder_id"));
            return map;
        }).collect(Collectors.toList());

        return Result.success(results);
    }

    @PostMapping("/upload")
    public Result<?> uploadDocument(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "isPublic", defaultValue = "false") Boolean isPublic,
                            @RequestParam(value = "spaceId", required = false) Long spaceId,
                            @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} uploads document {}, isPublic={}, spaceId={}, folderId={}",
                userId, file.getOriginalFilename(), isPublic, spaceId, folderId);
        return documentService.uploadDocument(file, userId, isPublic, spaceId, folderId);
    }

    @GetMapping("/list")
    public Result<?> listDocument(@RequestParam(value = "spaceId", required = false) Long spaceId,
                          @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} queries documents, spaceId={}, folderId={}", userId, spaceId, folderId);
        return documentService.listDocuments(userId, spaceId, folderId);
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteDocument(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} requests deleting document {}", userId, id);
        return documentService.deleteDocument(id, userId);
    }
}
