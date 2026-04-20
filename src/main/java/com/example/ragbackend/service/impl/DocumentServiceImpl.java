package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.service.DocumentProcessingService;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.service.SpaceAccessService;
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

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private DocumentProcessingService documentProcessingService;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private SpaceAccessService spaceAccessService;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    @Transactional
    public Result<?> uploadDocument(MultipartFile file, Long userId, Long spaceId, Long folderId) {
        try {
            validateUploadPermission(userId, spaceId, folderId);

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
            document.setMimeType(file.getContentType());
            document.setStatus(STATUS_PENDING);
            document.setIsDeleted(false);
            document.setDeleteTime(null);
            document.setErrorMessage(null);
            document.setRetryCount(0);
            document.setLastRetryTime(null);
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            save(document);

            registerAsyncProcessing(document.getId());
            log.info("Document uploaded, userId={}, documentId={}, spaceId={}, folderId={}, fileName={}",
                    userId, document.getId(), spaceId, folderId, originalFilename);
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
            applySpaceAndFolderFilter(queryWrapper, spaceId, folderId);
            return Result.success(list(queryWrapper));
        }

        List<Long> accessibleSpaceIds = spaceAccessService.getAccessibleSpaceIds(userId);
        if (spaceId != null && !spaceAccessService.canAccessSpace(userId, spaceId)) {
            return Result.error(403, "You do not have permission to view this space");
        }

        if (spaceId != null) {
            queryWrapper.eq(Document::getSpaceId, spaceId);
        } else if (accessibleSpaceIds.isEmpty()) {
            return Result.success(List.of());
        } else {
            queryWrapper.in(Document::getSpaceId, accessibleSpaceIds);
        }

        if (folderId != null) {
            queryWrapper.eq(Document::getFolderId, folderId);
        }

        return Result.success(list(queryWrapper));
    }

    @Override
    @Transactional
    public Result<?> deleteDocument(Long id, Long userId) {
        Document document = getAccessibleDocument(id, userId);
        ensureDocumentManagePermission(document, userId);

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(document.getMinioPath())
                    .build());
        } catch (Exception ex) {
            log.error("Failed to delete MinIO object, documentId={}, error={}", id, ex.getMessage(), ex);
        }

        try {
            embeddingStore.removeAll(metadataKey("document_id").isEqualTo(String.valueOf(id)));
        } catch (Exception ex) {
            log.error("Failed to delete document vectors, documentId={}, error={}", id, ex.getMessage(), ex);
        }

        document.setIsDeleted(true);
        document.setDeleteTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        updateById(document);
        return Result.success("Document deleted successfully");
    }

    @Override
    @Transactional
    public Result<?> retryDocument(Long id, Long userId) {
        Document document = getAccessibleDocument(id, userId);
        ensureDocumentManagePermission(document, userId);

        if (!isFailedStatus(document.getStatus())) {
            throw new BusinessException(400, "Only failed documents can be retried");
        }

        try {
            embeddingStore.removeAll(metadataKey("document_id").isEqualTo(String.valueOf(id)));
        } catch (Exception ex) {
            log.warn("Failed to clear existing vectors before retry, documentId={}, error={}", id, ex.getMessage());
        }

        document.setStatus(STATUS_PENDING);
        document.setErrorMessage(null);
        document.setRetryCount(document.getRetryCount() == null ? 1 : document.getRetryCount() + 1);
        document.setLastRetryTime(LocalDateTime.now());
        document.setUpdateTime(LocalDateTime.now());
        updateById(document);
        registerAsyncProcessing(id);
        log.info("Retry document processing, userId={}, documentId={}, retryCount={}",
                userId, id, document.getRetryCount());
        return Result.success(document);
    }

    @Override
    public Document getAccessibleDocument(Long id, Long userId) {
        Document document = getById(id);
        if (document == null || Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new BusinessException(404, "Document record does not exist");
        }
        if (SecurityUtils.isAdmin()) {
            return document;
        }
        if (!spaceAccessService.canAccessSpace(userId, document.getSpaceId())) {
            throw new BusinessException(403, "You do not have permission to access this document");
        }
        return document;
    }

    private void validateUploadPermission(Long userId, Long spaceId, Long folderId) {
        if (spaceId == null) {
            throw new BusinessException(400, "spaceId is required");
        }
        if (!spaceAccessService.canUpload(userId, spaceId, SecurityUtils.isAdmin())) {
            throw new BusinessException(403, "You do not have upload permission in the target knowledge space");
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

    private void ensureDocumentManagePermission(Document document, Long userId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (userId.equals(document.getUserId())) {
            return;
        }
        SpaceMember membership = spaceAccessService.getMembership(userId, document.getSpaceId());
        if (membership == null || !SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(403, "You do not have permission to manage this document");
        }
    }

    private void applySpaceAndFolderFilter(LambdaQueryWrapper<Document> queryWrapper, Long spaceId, Long folderId) {
        if (spaceId != null) {
            queryWrapper.eq(Document::getSpaceId, spaceId);
        }
        if (folderId != null) {
            queryWrapper.eq(Document::getFolderId, folderId);
        }
    }

    private boolean isFailedStatus(Integer status) {
        return status != null && status == STATUS_FAILED;
    }

    private void registerAsyncProcessing(Long documentId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    documentProcessingService.processDocumentAsync(documentId);
                }
            });
        } else {
            documentProcessingService.processDocumentAsync(documentId);
        }
    }

    private String resolveFileType(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "unknown";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}
