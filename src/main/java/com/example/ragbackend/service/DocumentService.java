package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends IService<Document> {

    Result<?> uploadDocument(MultipartFile file, Long userId, Long spaceId, Long folderId);

    Result<?> listDocuments(Long userId, Long spaceId, Long folderId);

    Result<?> deleteDocument(Long id, Long userId);

    Result<?> retryDocument(Long id, Long userId);

    Document getAccessibleDocument(Long id, Long userId);

    void purgeDocumentsByFolderIds(List<Long> folderIds);

    void purgeDocumentsBySpaceId(Long spaceId);
}
