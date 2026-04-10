package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.service.DocumentProcessingService;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.utils.SecurityUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    @Transactional
    public Result<?> uploadDocument(MultipartFile file, Long userId, Boolean isPublic) {
        log.info("开始处理文件上传, fileName={}, userId={}, isPublic={}", file.getOriginalFilename(), userId, isPublic);
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            log.info("文件上传开始, userId={}, fileName={}, size={}, extension={}",
                    userId, originalFilename, file.getSize(), extension);

            String minioPath = "documents/" + userId + "/" + UUID.randomUUID() + extension;
            log.info("上传文件至 MinIO, userId={}, minioPath={}", userId, minioPath);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(minioPath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("MinIO 上传完成, userId={}, minioPath={}", userId, minioPath);

            Document document = new Document();
            document.setUserId(userId);
            document.setFileName(file.getOriginalFilename());
            document.setMinioPath(minioPath);
            document.setFileSize(file.getSize());
            document.setStatus(0);
            document.setIsPublic(isPublic != null && isPublic);
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            this.save(document);
            log.info("Document 记录已保存, documentId={}, userId={}, status=0(待处理)", document.getId(), userId);

            // 3. 异步触发后端文档解析与处理
            // 关键修复：确保在事务提交之后再触发异步任务，防止异步线程查不到尚未提交的数据库记录
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        documentProcessingService.processDocumentAsync(document.getId(), document.getIsPublic());
                        log.info("事务已提交，异步处理任务已触发, documentId={}", document.getId());
                    }
                });
            } else {
                // 如果没有事务环境，则直接触发
                documentProcessingService.processDocumentAsync(document.getId(), document.getIsPublic());
            }

            return Result.success(document);
        } catch (Exception e) {
            log.error("Document upload failed, userId={}, fileName={}, error={}",
                    userId, file.getOriginalFilename(), e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result<?> listDocuments(Long userId) {
        List<Document> documents;
        if (SecurityUtils.isAdmin()) {
            log.info("管理员 {} 正在查询全量文档列表", userId);
            documents = this.list();
        } else {
            log.info("用户 {} 正在查询可见文档列表", userId);
            documents = this.list(new LambdaQueryWrapper<Document>()
                    .eq(Document::getUserId, userId)
                    .or().eq(Document::getIsPublic, true)
                    .orderByDesc(Document::getCreateTime));
        }
        return Result.success(documents);
    }

    @Override
    @Transactional
    public Result<?> deleteDocument(Long id, Long userId) {
        Document document = this.getById(id);
        if (document == null) {
            log.warn("删除文件失败, documentId={}, userId={}, 记录不存在", id, userId);
            return Result.error("文件记录不存在");
        }
        if (!document.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            log.warn("拒绝未经授权的删除请求, userId={}, documentId={}", userId, id);
            return Result.error("您没有权限删除此文件");
        }

        log.info("开始删除文件, documentId={}, userId={}, minioPath={}", id, userId, document.getMinioPath());
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .build()
            );
            log.info("MinIO 文件删除完成, documentId={}", id);
        } catch (Exception e) {
            log.error("MinIO 文件删除失败, documentId={}, path={}, error={}",
                    id, document.getMinioPath(), e.getMessage(), e);
        }

        // 3. 从 Chroma 向量库同步删除相关片段
        try {
            log.info("开始从 Chroma 删除向量片段, documentId={}", id);
            embeddingStore.removeAll(metadataKey("document_id").isEqualTo(String.valueOf(id)));
            log.info("Chroma 向量片段删除完成, documentId={}", id);
        } catch (Exception e) {
            log.error("Chroma 向量片段删除失败, documentId={}, error={}", id, e.getMessage(), e);
            // 向量库删除失败通常不应阻塞主流程，但需记录日志
        }

        this.removeById(id);
        log.info("数据库记录删除完成, documentId={}", id);
        return Result.success("删除成功");
    }
}
