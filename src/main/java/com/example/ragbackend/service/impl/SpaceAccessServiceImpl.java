package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        return spaceMemberMapper.selectCount(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getSpaceId, spaceId)) > 0;
    }

    @Override
    public Filter buildSearchFilter(Long userId, boolean isAdmin, Long spaceId, Long folderId) {
        Filter filter;
        if (isAdmin) {
            filter = buildAdminFilter(spaceId);
        } else {
            filter = buildAccessibleSpacesFilter(userId, spaceId);
        }

        Filter folderFilter = folderId == null ? null : metadataKey("folder_id").isEqualTo(String.valueOf(folderId));
        if (folderFilter != null) {
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
