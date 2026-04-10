package com.example.ragbackend.service;

public interface DocumentProcessingService {

    void processDocumentAsync(Long documentId, Boolean isPublic);
}
