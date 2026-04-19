package com.example.ragbackend.service;

import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;
import com.example.ragbackend.service.impl.KnowledgeSpaceServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Spy
    @InjectMocks
    private KnowledgeSpaceServiceImpl knowledgeSpaceService;

    @Test
    void createSpaceShouldRejectNonSuperAdmin() {
        KnowledgeSpaceCreateDTO dto = new KnowledgeSpaceCreateDTO();
        dto.setName("研发部");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeSpaceService.createSpace(1L, false, dto));

        assertEquals(403, exception.getCode());
    }

    @Test
    void addMemberShouldInsertAsMemberRole() {
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
                        && SpaceJoinRequestConstants.MEMBER_ROLE.equals(member.getRole())));
    }

    @Test
    void updateMemberRoleShouldRequireSuperAdmin() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeSpaceService.updateMemberRole(10L, 2L, 1L, false, "ADMIN"));

        assertEquals(403, exception.getCode());
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
}
