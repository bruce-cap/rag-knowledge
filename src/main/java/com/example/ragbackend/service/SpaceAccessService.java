package com.example.ragbackend.service;

import com.example.ragbackend.entity.SpaceMember;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.List;

public interface SpaceAccessService {

    List<Long> getAccessibleSpaceIds(Long userId);

    boolean canAccessSpace(Long userId, Long spaceId);

    SpaceMember getMembership(Long userId, Long spaceId);

    boolean canUpload(Long userId, Long spaceId, boolean isSuperAdmin);

    Filter buildSearchFilter(Long userId, boolean isAdmin, Long spaceId, Long folderId);
}
