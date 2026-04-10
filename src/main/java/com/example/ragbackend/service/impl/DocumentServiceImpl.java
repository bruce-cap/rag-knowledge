package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.service.DocumentProcessingService;
import com.example.ragbackend.service.DocumentService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    public Result<?> uploadDocument(MultipartFile file, Long userId) {
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
            document.setFileName(originalFilename);
            document.setMinioPath(minioPath);
            document.setFileSize(file.getSize());
            document.setStatus(0);
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            this.save(document);
            log.info("Document 记录已保存, documentId={}, userId={}, status=0(待处理)", document.getId(), userId);

            documentProcessingService.processDocumentAsync(document.getId());
            log.info("异步处理任务已触发, documentId={}", document.getId());
            return Result.success(document);
        } catch (Exception e) {
            log.error("Document upload failed, userId={}, fileName={}, error={}",
                    userId, file.getOriginalFilename(), e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result<?> listDocuments(Long userId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getUserId, userId)
                .orderByDesc(Document::getCreateTime);
        List<Document> documents = this.list(queryWrapper);
        log.info("查询文件列表完成, userId={}, count={}", userId, documents.size());
        return Result.success(documents);
    }

    @Override
    public Result<?> deleteDocument(Long id, Long userId) {
        Document document = this.getById(id);
        if (document == null) {
            log.warn("删除文件失败, documentId={}, userId={}, 记录不存在", id, userId);
            return Result.error("文件记录不存在");
        }
        if (!document.getUserId().equals(userId)) {
            log.warn("删除文件失败, documentId={}, userId={}, 越权操作", id, userId);
            return Result.error("越权操作：只能删除自己上传的文件");
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

        this.removeById(id);
        log.info("数据库记录删除完成, documentId={}", id);
        return Result.success("删除成功");
    }
}
