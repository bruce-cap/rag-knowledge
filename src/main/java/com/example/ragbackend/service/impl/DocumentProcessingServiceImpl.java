package com.example.ragbackend.service.impl;

import com.example.ragbackend.entity.Document;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.service.DashScopeEmbeddingService;
import com.example.ragbackend.service.DocumentProcessingService;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
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
    public void processDocumentAsync(Long documentId, Boolean isPublic) {
        log.info("Start processing uploaded document asynchronously, documentId={}, isPublic={}", documentId, isPublic);

        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Document 记录不存在，跳过处理, documentId={}", documentId);
            return;
        }

        try {
            // 更新状态为处理中
            document.setStatus(1);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);
            log.info("状态已更新为处理中, documentId={}, status=1", documentId);

            // 从 MinIO 下载文件
            log.info("从 MinIO 下载文件, documentId={}, minioPath={}", documentId, document.getMinioPath());
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .build()
            )) {
                log.info("MinIO 文件下载完成, documentId={}", documentId);

                // 解析文档
                log.info("开始使用 ApacheTika 解析文档, documentId={}", documentId);
                DocumentParser parser = new ApacheTikaDocumentParser();
                dev.langchain4j.data.document.Document parsedDocument = parser.parse(inputStream);
                log.info("文档解析完成, documentId={}", documentId);

                // 分块
                log.info("开始文档分块, documentId={}, chunkSize=500, overlap=50", documentId);
                List<TextSegment> segments = DocumentSplitters.recursive(500, 50).split(parsedDocument);
                
                // 为每个分块添加元数据，方便后续按文件删除或按用户过滤
                if (segments != null) {
                    segments.forEach(segment -> {
                        segment.metadata().add("document_id", String.valueOf(documentId));
                        segment.metadata().add("user_id", String.valueOf(document.getUserId()));
                        segment.metadata().add("is_public", String.valueOf(isPublic != null && isPublic));
                    });
                }
                
                log.info("文档分块完成, documentId={}, segments 数量={}", documentId, segments == null ? 0 : segments.size());

                if (segments == null || segments.isEmpty()) {
                    throw new IllegalStateException("No text segments extracted from document");
                }

                // 生成 Embedding
                log.info("开始生成 Embedding, documentId={}, segments={}", documentId, segments.size());
                List<Embedding> embeddings = dashScopeEmbeddingService.embedSegments(segments);
                log.info("Embedding 生成完成, documentId={}, embeddings={}", documentId, embeddings.size());

                // 存入 Chroma
                log.info("开始存入 Chroma 向量数据库, documentId={}", documentId);
                embeddingStore.addAll(embeddings, segments);
                log.info("Chroma 存储完成, documentId={}", documentId);

                document.setStatus(2);
                log.info("========== 文档处理成功 ==========, documentId={}, fileName={}, segments={}",
                        documentId, document.getFileName(), segments.size());
            }
        } catch (Exception e) {
            document.setStatus(3);
            log.error("========== 文档处理失败 ==========, documentId={}, fileName={}, error={}",
                    documentId, document.getFileName(), e.getMessage(), e);
        } finally {
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);
        }
    }
}
