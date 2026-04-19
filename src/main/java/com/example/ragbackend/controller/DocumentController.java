package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.model.dto.SearchRequestDTO;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.service.SpaceAccessService;
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

    @Autowired
    private SpaceAccessService spaceAccessService;

    @PostMapping("/search")
    public Result<?> searchDocument(@RequestBody SearchRequestDTO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        log.info("User {} requests vector search, query={}, spaceId={}, folderId={}",
                userId, request.getQuery(), request.getSpaceId(), request.getFolderId());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return Result.error("Search query cannot be empty");
        }

        if (!isAdmin && request.getSpaceId() != null && !spaceAccessService.canAccessSpace(userId, request.getSpaceId())) {
            return Result.error(403, "You do not have permission to search this space");
        }

        Embedding queryEmbedding = dashScopeEmbeddingService.embedSegments(List.of(TextSegment.from(request.getQuery()))).get(0);
        Filter filter = spaceAccessService.buildSearchFilter(userId, isAdmin, request.getSpaceId(), request.getFolderId());

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
                                    @RequestParam("spaceId") Long spaceId,
                                    @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} uploads document {}, spaceId={}, folderId={}",
                userId, file.getOriginalFilename(), spaceId, folderId);
        return documentService.uploadDocument(file, userId, spaceId, folderId);
    }

    @GetMapping("/list")
    public Result<?> listDocument(@RequestParam(value = "spaceId", required = false) Long spaceId,
                                  @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} queries documents, spaceId={}, folderId={}", userId, spaceId, folderId);
        return documentService.listDocuments(userId, spaceId, folderId);
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> deleteDocument(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("User {} requests deleting document {}", userId, id);
        return documentService.deleteDocument(id, userId);
    }
}
