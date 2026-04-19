package com.example.ragbackend.service;

import dev.langchain4j.store.embedding.filter.Filter;

import java.util.List;

public interface SpaceAccessService {

    List<Long> getAccessibleSpaceIds(Long userId);

    boolean canAccessSpace(Long userId, Long spaceId);

    Filter buildSearchFilter(Long userId, boolean isAdmin, Long spaceId, Long folderId);
}
