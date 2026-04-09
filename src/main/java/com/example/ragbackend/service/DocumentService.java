package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService extends IService<Document> {

    /**
     * 上传文档到MinIO，并保存元数据到MySQL
     * @param file 上传的文件
     * @param userId 上传者ID
     * @return 返回上传结果
     */
    Result<?> uploadDocument(MultipartFile file, Long userId);

    /**
     * 查询用户的所有上传文档
     * @param userId 上传者ID
     * @return 返回文档列表
     */
    Result<?> listDocuments(Long userId);

    /**
     * 删除指定文档
     * @param id 文档ID
     * @param userId 操作者ID，用于越权校验
     * @return 删除结果
     */
    Result<?> deleteDocument(Long id, Long userId);
}