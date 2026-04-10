package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
@CrossOrigin
@Slf4j
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 上传文件: {}", userId, file.getOriginalFilename());
        return documentService.uploadDocument(file, userId);
    }

    @GetMapping("/list")
    public Result<?> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 查询文件列表", userId);
        return documentService.listDocuments(userId);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 请求删除文件, documentId={}", userId, id);
        return documentService.deleteDocument(id, userId);
    }
}