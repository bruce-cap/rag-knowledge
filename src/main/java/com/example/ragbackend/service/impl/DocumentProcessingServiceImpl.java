package com.example.ragbackend.service.impl;

import com.example.ragbackend.entity.Document;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import com.example.ragbackend.service.DocumentProcessingService;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final DocumentMapper documentMapper;
    private final MinioClient minioClient;
    private final DashScopeEmbeddingService dashScopeEmbeddingService;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    @Async("documentProcessingExecutor")
    public void processDocumentAsync(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Document record not found, skip processing, documentId={}", documentId);
            return;
        }

        try {
            document.setStatus(1);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);

            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .build()
            )) {
                DocumentParser parser = new ApacheTikaDocumentParser();
                dev.langchain4j.data.document.Document parsedDocument = parser.parse(inputStream);
                List<TextSegment> segments = DocumentSplitters.recursive(500, 50).split(parsedDocument);

                if (segments == null || segments.isEmpty()) {
                    throw new IllegalStateException("No text segments extracted from document");
                }

                segments.forEach(segment -> {
                    segment.metadata().add("document_id", String.valueOf(documentId));
                    segment.metadata().add("user_id", String.valueOf(document.getUserId()));
                    segment.metadata().add("space_id", String.valueOf(document.getSpaceId()));
                    if (document.getFolderId() != null) {
                        segment.metadata().add("folder_id", String.valueOf(document.getFolderId()));
                    }
                });

                int totalSegments = segments.size();
                int batchSize = 100;
                int totalBatches = (int) Math.ceil((double) totalSegments / batchSize);

                for (int i = 0; i < totalSegments; i += batchSize) {
                    int end = Math.min(i + batchSize, totalSegments);
                    List<TextSegment> batchSegments = segments.subList(i, end);
                    List<Embedding> batchEmbeddings = dashScopeEmbeddingService.embedSegments(batchSegments);
                    embeddingStore.addAll(batchEmbeddings, batchSegments);
                    log.info("Processed document batch {}/{} for documentId={}", (i / batchSize) + 1, totalBatches, documentId);
                }

                document.setStatus(2);
                document.setErrorMessage(null);
            }
        } catch (Exception ex) {
            document.setStatus(3);
            document.setErrorMessage(ex.getMessage());
            log.error("Document processing failed, documentId={}, error={}", documentId, ex.getMessage(), ex);
        } finally {
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);
        }
    }
}
