package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.utils.SecurityUtils;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.ragbackend.model.dto.SearchRequestDTO;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

    @PostMapping("/search")
    public Result<?> search(@RequestBody SearchRequestDTO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 发起向量搜索, query: {}", userId, request.getQuery());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return Result.error("搜索请求不能为空");
        }

        // 1. 向量化问题
        Embedding queryEmbedding = dashScopeEmbeddingService.embedSegments(
                List.of(TextSegment.from(request.getQuery())))
                .get(0);

        // 2. 构造查询请求：管理员不设限，普通用户只能看自己和公开的
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

        // 3. 原生检索
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // 4. 组装返回结果
        List<Map<String, Object>> results = searchResult.matches().stream().map(match -> {
            Map<String, Object> map = new HashMap<>();
            map.put("text", match.embedded().text());
            map.put("score", match.score());
            map.put("documentId", match.embedded().metadata().getString("document_id"));
            return map;
        }).collect(Collectors.toList());

        return Result.success(results);
    }

    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file, 
                            @RequestParam(value = "isPublic", defaultValue = "false") Boolean isPublic) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 上传文件: {}, isPublic={}", userId, file.getOriginalFilename(), isPublic);
        return documentService.uploadDocument(file, userId, isPublic);
    }

    @GetMapping("/list")
    public Result<?> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 查询文件列表", userId);
        return documentService.listDocuments(userId);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 请求删除文件, documentId={}", userId, id);
        return documentService.deleteDocument(id, userId);
    }
}