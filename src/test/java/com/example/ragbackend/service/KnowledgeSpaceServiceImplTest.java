package com.example.ragbackend.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.mapper.SpaceJoinRequestMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.SpaceRoleRequestMapper;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.KnowledgeSpaceUpdateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;
import com.example.ragbackend.service.impl.KnowledgeSpaceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSpaceServiceImplTest {

    @Mock
    private SpaceMemberMapper spaceMemberMapper;

    @Mock
    private FolderMapper folderMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SpaceJoinRequestMapper spaceJoinRequestMapper;

    @Mock
    private SpaceRoleRequestMapper spaceRoleRequestMapper;

    @Mock
    private DocumentService documentService;

    @Spy
    @InjectMocks
    private KnowledgeSpaceServiceImpl knowledgeSpaceService;

    @Test
    void createSpaceShouldRejectNonSuperAdmin() {
        KnowledgeSpaceCreateDTO dto = new KnowledgeSpaceCreateDTO();
        dto.setName("R&D");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeSpaceService.createSpace(1L, false, dto));

        assertEquals(403, exception.getCode());
    }

    @Test
    void addMemberShouldUseRequestedRoleForSuperAdmin() {
        SpaceMemberAddDTO dto = new SpaceMemberAddDTO();
        dto.setUserId(2L);
        dto.setRole("ADMIN");

        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        doReturn(space).when(knowledgeSpaceService).getById(10L);

        User user = new User();
        user.setId(2L);
        user.setRole("USER");
        when(userMapper.selectById(2L)).thenReturn(user);
        when(spaceMemberMapper.selectOne(any())).thenReturn(null);

        knowledgeSpaceService.addMember(10L, 99L, true, dto);

        verify(spaceMemberMapper).insert(argThat((SpaceMember member) ->
                member.getSpaceId().equals(10L)
                        && member.getUserId().equals(2L)
                        && SpaceJoinRequestConstants.ADMIN_ROLE.equals(member.getRole())));
    }

    @Test
    void addMemberShouldRejectSpaceAdminInvitingAdmin() {
        SpaceMemberAddDTO dto = new SpaceMemberAddDTO();
        dto.setUserId(2L);
        dto.setRole("ADMIN");

        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        doReturn(space).when(knowledgeSpaceService).getById(10L);

        SpaceMember operatorMembership = new SpaceMember();
        operatorMembership.setSpaceId(10L);
        operatorMembership.setUserId(5L);
        operatorMembership.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        when(spaceMemberMapper.selectOne(any())).thenReturn(operatorMembership);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeSpaceService.addMember(10L, 5L, false, dto));

        assertEquals(403, exception.getCode());
    }

    @Test
    void updateMemberRoleShouldAllowSpaceAdminToAdjustLowerRole() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        doReturn(space).when(knowledgeSpaceService).getById(10L);

        SpaceMember targetMember = new SpaceMember();
        targetMember.setId(8L);
        targetMember.setSpaceId(10L);
        targetMember.setUserId(2L);
        targetMember.setRole(SpaceJoinRequestConstants.VIEW_ROLE);

        SpaceMember operatorMembership = new SpaceMember();
        operatorMembership.setId(9L);
        operatorMembership.setSpaceId(10L);
        operatorMembership.setUserId(5L);
        operatorMembership.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        when(spaceMemberMapper.selectOne(any())).thenReturn(targetMember, operatorMembership);

        SpaceMember updated = knowledgeSpaceService.updateMemberRole(10L, 2L, 5L, false, "MEMBER");

        assertEquals(SpaceJoinRequestConstants.MEMBER_ROLE, updated.getRole());
        verify(spaceMemberMapper).updateById(targetMember);
    }

    @Test
    void updateMemberRoleShouldRejectSpaceAdminPromotingToAdmin() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        doReturn(space).when(knowledgeSpaceService).getById(10L);

        SpaceMember targetMember = new SpaceMember();
        targetMember.setId(8L);
        targetMember.setSpaceId(10L);
        targetMember.setUserId(2L);
        targetMember.setRole(SpaceJoinRequestConstants.MEMBER_ROLE);

        SpaceMember operatorMembership = new SpaceMember();
        operatorMembership.setId(9L);
        operatorMembership.setSpaceId(10L);
        operatorMembership.setUserId(5L);
        operatorMembership.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        when(spaceMemberMapper.selectOne(any())).thenReturn(targetMember, operatorMembership);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeSpaceService.updateMemberRole(10L, 2L, 5L, false, "ADMIN"));

        assertEquals(403, exception.getCode());
    }

    @Test
    void updateSpaceShouldAllowSpaceAdmin() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        space.setName("Old Name");
        doReturn(space).when(knowledgeSpaceService).getById(10L);

        SpaceMember adminMember = new SpaceMember();
        adminMember.setSpaceId(10L);
        adminMember.setUserId(5L);
        adminMember.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);
        when(spaceMemberMapper.selectOne(any())).thenReturn(adminMember);
        doReturn(true).when(knowledgeSpaceService).updateById(any(KnowledgeSpace.class));

        KnowledgeSpaceUpdateDTO dto = new KnowledgeSpaceUpdateDTO();
        dto.setName("New Name");

        KnowledgeSpace updated = knowledgeSpaceService.updateSpace(10L, 5L, false, dto);

        assertEquals("New Name", updated.getName());
        verify(knowledgeSpaceService).updateById(updated);
    }

    @Test
    void listAllSpacesShouldExcludeJoinedSpacesForOrdinaryUser() {
        SpaceMember joinedMember = new SpaceMember();
        joinedMember.setUserId(5L);
        joinedMember.setSpaceId(10L);
        when(spaceMemberMapper.selectList(any())).thenReturn(java.util.List.of(joinedMember));

        KnowledgeSpace availableSpace = new KnowledgeSpace();
        availableSpace.setId(20L);
        availableSpace.setIsSystem(false);
        doReturn(java.util.List.of(availableSpace)).when(knowledgeSpaceService)
                .list(org.mockito.ArgumentMatchers.<Wrapper<KnowledgeSpace>>any());

        java.util.List<KnowledgeSpace> spaces = knowledgeSpaceService.listAllSpaces(5L, false);

        assertEquals(1, spaces.size());
        assertEquals(20L, spaces.get(0).getId());
        assertFalse(Boolean.TRUE.equals(spaces.get(0).getIsSystem()));
    }

    @Test
    void deleteSpaceShouldCascadeCleanupBeforeRemovingSpace() {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        space.setIsSystem(false);

        Folder root = new Folder();
        root.setId(1L);
        root.setSpaceId(10L);

        Folder child = new Folder();
        child.setId(2L);
        child.setSpaceId(10L);
        child.setParentId(1L);

        doReturn(space).when(knowledgeSpaceService).getById(10L);
        when(folderMapper.selectList(any())).thenReturn(List.of(root, child));
        doReturn(true).when(knowledgeSpaceService).removeById(10L);

        knowledgeSpaceService.deleteSpace(10L, 99L, true);

        verify(documentService).purgeDocumentsBySpaceId(10L);
        verify(folderMapper).deleteById(2L);
        verify(folderMapper).deleteById(1L);
        verify(spaceJoinRequestMapper).delete(any());
        verify(spaceRoleRequestMapper).delete(any());
        verify(spaceMemberMapper).delete(any());
        verify(knowledgeSpaceService).removeById(10L);
    }
}
