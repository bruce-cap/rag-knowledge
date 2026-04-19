package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.service.DocumentProcessingService;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.utils.SecurityUtils;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private SpaceMemberMapper spaceMemberMapper;

    @Autowired
    private FolderMapper folderMapper;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    @Transactional
    public Result<?> uploadDocument(MultipartFile file, Long userId, Boolean isPublic, Long spaceId, Long folderId) {
        try {
            validateDocumentOwnershipPlacement(userId, spaceId, folderId);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String minioPath = "documents/" + userId + "/" + UUID.randomUUID() + extension;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(minioPath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            Document document = new Document();
            document.setSpaceId(spaceId);
            document.setFolderId(folderId);
            document.setUserId(userId);
            document.setFileName(originalFilename);
            document.setMinioPath(minioPath);
            document.setFileSize(file.getSize());
            document.setFileType(resolveFileType(originalFilename));
            document.setStatus(0);
            document.setIsDeleted(false);
            document.setDeleteTime(null);
            document.setErrorMessage(null);
            document.setIsPublic(isPublic != null && isPublic);
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            this.save(document);

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        documentProcessingService.processDocumentAsync(document.getId(), document.getIsPublic());
                    }
                });
            } else {
                documentProcessingService.processDocumentAsync(document.getId(), document.getIsPublic());
            }

            return Result.success(document);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Document upload failed, userId={}, fileName={}, error={}",
                    userId, file.getOriginalFilename(), ex.getMessage(), ex);
            return Result.error("Upload failed: " + ex.getMessage());
        }
    }

    @Override
    public Result<?> listDocuments(Long userId, Long spaceId, Long folderId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getIsDeleted, false)
                .orderByDesc(Document::getCreateTime);

        if (SecurityUtils.isAdmin()) {
            if (spaceId != null) {
                queryWrapper.eq(Document::getSpaceId, spaceId);
            }
            if (folderId != null) {
                queryWrapper.eq(Document::getFolderId, folderId);
            }
            return Result.success(this.list(queryWrapper));
        }

        queryWrapper.and(wrapper -> wrapper.eq(Document::getUserId, userId).or().eq(Document::getIsPublic, true));
        if (spaceId != null) {
            queryWrapper.eq(Document::getSpaceId, spaceId);
        }
        if (folderId != null) {
            queryWrapper.eq(Document::getFolderId, folderId);
        }

        return Result.success(this.list(queryWrapper));
    }

    @Override
    @Transactional
    public Result<?> deleteDocument(Long id, Long userId) {
        Document document = this.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getIsDeleted())) {
            return Result.error("Document record does not exist");
        }
        if (!document.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            return Result.error("You do not have permission to delete this document");
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(document.getMinioPath())
                            .build()
            );
        } catch (Exception ex) {
            log.error("Failed to delete MinIO object, documentId={}, error={}", id, ex.getMessage(), ex);
        }

        try {
            embeddingStore.removeAll(metadataKey("document_id").isEqualTo(String.valueOf(id)));
        } catch (Exception ex) {
            log.error("Failed to delete document vectors, documentId={}, error={}", id, ex.getMessage(), ex);
        }

        this.removeById(id);
        return Result.success("Document deleted successfully");
    }

    private void validateDocumentOwnershipPlacement(Long userId, Long spaceId, Long folderId) {
        if (folderId != null && spaceId == null) {
            throw new BusinessException(400, "spaceId is required when folderId is provided");
        }

        if (spaceId != null && !SecurityUtils.isAdmin()) {
            SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                    .eq(SpaceMember::getSpaceId, spaceId)
                    .eq(SpaceMember::getUserId, userId));
            if (membership == null) {
                throw new BusinessException(403, "You do not belong to the target knowledge space");
            }
        }

        if (folderId != null) {
            Folder folder = folderMapper.selectById(folderId);
            if (folder == null) {
                throw new BusinessException(404, "Folder not found");
            }
            if (!folder.getSpaceId().equals(spaceId)) {
                throw new BusinessException(400, "Folder does not belong to the specified space");
            }
        }
    }

    private String resolveFileType(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "unknown";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}
