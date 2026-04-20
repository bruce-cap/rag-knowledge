package com.example.ragbackend.service;

import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.service.impl.SpaceAccessServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceAccessServiceImplTest {

    @Mock
    private SpaceMemberMapper spaceMemberMapper;

    @InjectMocks
    private SpaceAccessServiceImpl spaceAccessService;

    @Test
    void shouldReturnDistinctAccessibleSpaceIds() {
        when(spaceMemberMapper.selectList(any())).thenReturn(List.of(
                createMember(1L, 10L),
                createMember(1L, 10L),
                createMember(1L, 20L)
        ));

        List<Long> accessibleSpaceIds = spaceAccessService.getAccessibleSpaceIds(1L);

        assertEquals(List.of(10L, 20L), accessibleSpaceIds);
    }

    @Test
    void shouldCheckSpaceAccessByMembership() {
        when(spaceMemberMapper.selectOne(any())).thenReturn(createMember(1L, 10L)).thenReturn(null);

        assertTrue(spaceAccessService.canAccessSpace(1L, 10L));
        assertFalse(spaceAccessService.canAccessSpace(1L, 30L));
        assertTrue(spaceAccessService.canAccessSpace(1L, null));
    }

    @Test
    void shouldBuildSearchFilterForAccessibleSpacesAndPublicDocuments() {
        when(spaceMemberMapper.selectList(any())).thenReturn(List.of(
                createMember(1L, 10L),
                createMember(1L, 20L)
        ));

        assertNotNull(spaceAccessService.buildSearchFilter(1L, false, null, null));
        assertNotNull(spaceAccessService.buildSearchFilter(1L, false, 10L, 100L));
        assertNotNull(spaceAccessService.buildSearchFilter(1L, true, 10L, 100L));
    }

    private SpaceMember createMember(Long userId, Long spaceId) {
        SpaceMember member = new SpaceMember();
        member.setUserId(userId);
        member.setSpaceId(spaceId);
        return member;
    }
}
