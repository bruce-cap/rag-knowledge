package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.service.DocumentService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.minio.RemoveObjectArgs;

@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    public Result<?> uploadDocument(MultipartFile file, Long userId) {
        try {
            // 1. 生成唯一的MinIO路径
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String minioPath = "documents/" + userId + "/" + UUID.randomUUID() + extension;

            // 2. 上传文件到MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(minioPath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 3. 保存元数据到MySQL
            Document document = new Document();
            document.setUserId(userId);
            document.setFileName(originalFilename);
            document.setMinioPath(minioPath);
            document.setFileSize(file.getSize());
            document.setStatus(0); // 上传成功
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            this.save(document);

            return Result.success(document);
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result<?> listDocuments(Long userId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getUserId, userId)
                .orderByDesc(Document::getCreateTime);
        List<Document> documents = this.list(queryWrapper);
        return Result.success(documents);
    }

    @Override
    public Result<?> deleteDocument(Long id, Long userId) {
        Document document = this.getById(id);
        if (document == null) {
            return Result.error("文件记录不存在");
        }
        if (!document.getUserId().equals(userId)) {
            return Result.error("越权操作：只能删除自己上传的文件");
        }
        
        try {
            // 从 MinIO 删除对象
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(document.getMinioPath())
                    .build()
            );
        } catch (Exception e) {
            log.error("从 MinIO 删除文件失败: " + e.getMessage(), e);
            // 继续删除数据库中的记录，即便对象存储已异常
        }
        
        // 删除MySQL元数据
        this.removeById(id);
        return Result.success("删除成功");
    }
}