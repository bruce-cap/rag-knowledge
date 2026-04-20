package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
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
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private MinioClient minioClient;

    @org.springframework.beans.factory.annotation.Value("${minio.bucketName}")
    private String bucketName;

    @PostMapping("/search")
    public Result<?> searchDocument(@RequestBody SearchRequestDTO request) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        log.info("Vector search request, userId={}, query={}, spaceId={}, folderId={}",
                userId, request.getQuery(), request.getSpaceId(), request.getFolderId());

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return Result.error("Search query cannot be empty");
        }
        if (request.getFolderId() != null && request.getSpaceId() == null) {
            return Result.error(400, "spaceId is required when folderId is specified");
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
        log.info("Upload document, userId={}, spaceId={}, folderId={}, fileName={}",
                userId, spaceId, folderId, file.getOriginalFilename());
        return documentService.uploadDocument(file, userId, spaceId, folderId);
    }

    @GetMapping("/list")
    public Result<?> listDocument(@RequestParam(value = "spaceId", required = false) Long spaceId,
                                  @RequestParam(value = "folderId", required = false) Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return documentService.listDocuments(userId, spaceId, folderId);
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> deleteDocument(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return documentService.deleteDocument(id, userId);
    }

    @PostMapping("/retry/{id}")
    public Result<?> retryDocument(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return documentService.retryDocument(id, userId);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable("id") Long id) throws Exception {
        Long userId = SecurityUtils.getCurrentUserId();
        Document document = documentService.getAccessibleDocument(id, userId);
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(document.getMinioPath())
                .build())) {
            byte[] bytes = inputStream.readAllBytes();
            String contentType = document.getMimeType() == null || document.getMimeType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : document.getMimeType();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);
        }
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<byte[]> previewDocument(@PathVariable("id") Long id) throws Exception {
        Long userId = SecurityUtils.getCurrentUserId();
        Document document = documentService.getAccessibleDocument(id, userId);
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(document.getMinioPath())
                .build())) {
            byte[] bytes = inputStream.readAllBytes();
            String contentType = document.getMimeType() == null || document.getMimeType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : document.getMimeType();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);
        }
    }
}
