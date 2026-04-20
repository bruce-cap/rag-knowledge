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
import org.apache.tika.Tika;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/document")
@CrossOrigin
@Slf4j
public class DocumentController {

    private static final Set<String> TEXT_FILE_TYPES = Set.of("txt", "md");
    private static final Set<String> OFFICE_FILE_TYPES = Set.of("doc", "docx");

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

    private final Tika tika = new Tika();

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
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType(resolveContentType(document)))
                    .body(bytes);
        }
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<?> previewDocument(@PathVariable("id") Long id) throws Exception {
        Long userId = SecurityUtils.getCurrentUserId();
        Document document = documentService.getAccessibleDocument(id, userId);
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(document.getMinioPath())
                .build())) {
            byte[] bytes = inputStream.readAllBytes();
            String fileType = document.getFileType() == null ? "" : document.getFileType().toLowerCase();

            if ("pdf".equals(fileType)) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8))
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(bytes);
            }

            if (TEXT_FILE_TYPES.contains(fileType)) {
                Charset charset = detectCharset(bytes);
                String text = new String(bytes, charset);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                        .body(buildTextPreviewHtml(document.getFileName(), text, "md".equals(fileType)));
            }

            if (OFFICE_FILE_TYPES.contains(fileType)) {
                String extractedText = tika.parseToString(new ByteArrayInputStream(bytes));
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                        .body(buildTextPreviewHtml(document.getFileName(), extractedText, false));
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8))
                    .contentType(MediaType.parseMediaType(resolveContentType(document)))
                    .body(bytes);
        }
    }

    private String resolveContentType(Document document) {
        String mimeType = document.getMimeType();
        if (mimeType != null && !mimeType.isBlank() && !"application/octet-stream".equalsIgnoreCase(mimeType)) {
            if ("text/plain".equalsIgnoreCase(mimeType)) {
                return "text/plain;charset=UTF-8";
            }
            if ("text/markdown".equalsIgnoreCase(mimeType)) {
                return "text/markdown;charset=UTF-8";
            }
            return mimeType;
        }

        String fileType = document.getFileType() == null ? "" : document.getFileType().toLowerCase();
        return switch (fileType) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "md" -> "text/markdown;charset=UTF-8";
            case "txt" -> "text/plain;charset=UTF-8";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private Charset detectCharset(byte[] bytes) {
        CharsetDetector detector = new CharsetDetector();
        detector.setText(bytes);
        CharsetMatch match = detector.detect();
        if (match == null || match.getName() == null || match.getName().isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(match.getName());
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private String buildTextPreviewHtml(String fileName, String text, boolean markdownLike) {
        String escapedTitle = escapeHtml(fileName == null ? "Preview" : fileName);
        String body = markdownLike ? escapeHtml(text) : escapeHtml(text);
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <style>
                        body {
                            margin: 0;
                            padding: 24px;
                            background: #f8fafc;
                            color: #1e293b;
                            font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                        }
                        .container {
                            max-width: 960px;
                            margin: 0 auto;
                            background: #ffffff;
                            border: 1px solid #e2e8f0;
                            border-radius: 16px;
                            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
                            overflow: hidden;
                        }
                        .header {
                            padding: 16px 20px;
                            border-bottom: 1px solid #e2e8f0;
                            font-size: 18px;
                            font-weight: 600;
                        }
                        pre {
                            margin: 0;
                            padding: 20px;
                            white-space: pre-wrap;
                            word-break: break-word;
                            line-height: 1.7;
                            font-family: "Consolas", "Courier New", monospace;
                            font-size: 14px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">%s</div>
                        <pre>%s</pre>
                    </div>
                </body>
                </html>
                """.formatted(escapedTitle, escapedTitle, body);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
