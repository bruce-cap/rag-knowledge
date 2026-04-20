package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.service.SpaceAccessService;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class SpaceAccessServiceImpl implements SpaceAccessService {

    private static final String NO_ACCESS_SPACE_MARKER = "__NO_ACCESS__";

    @Autowired
    private SpaceMemberMapper spaceMemberMapper;

    @Override
    public List<Long> getAccessibleSpaceIds(Long userId) {
        return spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                        .eq(SpaceMember::getUserId, userId))
                .stream()
                .map(SpaceMember::getSpaceId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean canAccessSpace(Long userId, Long spaceId) {
        if (spaceId == null) {
            return true;
        }
        return getMembership(userId, spaceId) != null;
    }

    @Override
    public SpaceMember getMembership(Long userId, Long spaceId) {
        if (userId == null || spaceId == null) {
            return null;
        }
        return spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getSpaceId, spaceId)
                .last("LIMIT 1"));
    }

    @Override
    public boolean canUpload(Long userId, Long spaceId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return true;
        }
        SpaceMember membership = getMembership(userId, spaceId);
        if (membership == null) {
            return false;
        }
        return SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(membership.getRole())
                || SpaceJoinRequestConstants.MEMBER_ROLE.equalsIgnoreCase(membership.getRole());
    }

    @Override
    public Filter buildSearchFilter(Long userId, boolean isAdmin, Long spaceId, Long folderId) {
        Filter filter = isAdmin ? buildAdminFilter(spaceId) : buildAccessibleSpacesFilter(userId, spaceId);
        if (folderId != null) {
            Filter folderFilter = metadataKey("folder_id").isEqualTo(String.valueOf(folderId));
            filter = filter == null ? folderFilter : filter.and(folderFilter);
        }
        return filter;
    }

    private Filter buildAdminFilter(Long spaceId) {
        if (spaceId == null) {
            return null;
        }
        return metadataKey("space_id").isEqualTo(String.valueOf(spaceId));
    }

    private Filter buildAccessibleSpacesFilter(Long userId, Long requestedSpaceId) {
        List<Long> accessibleSpaceIds = getAccessibleSpaceIds(userId);
        if (requestedSpaceId != null) {
            if (!accessibleSpaceIds.contains(requestedSpaceId)) {
                return impossibleFilter();
            }
            return metadataKey("space_id").isEqualTo(String.valueOf(requestedSpaceId));
        }

        if (accessibleSpaceIds.isEmpty()) {
            return impossibleFilter();
        }

        Filter filter = null;
        for (Long accessibleSpaceId : accessibleSpaceIds) {
            Filter current = metadataKey("space_id").isEqualTo(String.valueOf(accessibleSpaceId));
            filter = filter == null ? current : filter.or(current);
        }
        return filter;
    }

    private Filter impossibleFilter() {
        return metadataKey("space_id").isEqualTo(NO_ACCESS_SPACE_MARKER);
    }
}
