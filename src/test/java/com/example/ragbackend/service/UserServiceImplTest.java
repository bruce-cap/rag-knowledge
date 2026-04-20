package com.example.ragbackend.service;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.constant.SystemSpaceConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Mock
    private SpaceMemberMapper spaceMemberMapper;

    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerShouldJoinPublicKnowledgeSpace() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("abc12345");
        dto.setConfirmPassword("abc12345");
        dto.setEmail("newuser@example.com");

        KnowledgeSpace publicSpace = new KnowledgeSpace();
        publicSpace.setId(10L);
        publicSpace.setCode(SystemSpaceConstants.PUBLIC_SPACE_CODE);

        when(knowledgeSpaceMapper.selectOne(any())).thenReturn(publicSpace);
        doReturn(0L).when(userService).count(any());
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(88L);
            return true;
        }).when(userService).save(any(User.class));

        Result<?> result = userService.register(dto);

        assertEquals(200, result.getCode());
        verify(spaceMemberMapper).insert(argThat((SpaceMember member) ->
                member.getSpaceId().equals(10L)
                        && member.getUserId().equals(88L)
                        && "VIEW".equals(member.getRole())));
    }

    @Test
    void registerShouldFailWhenPublicKnowledgeSpaceMissing() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("abc12345");
        dto.setConfirmPassword("abc12345");
        dto.setEmail("newuser@example.com");

        doReturn(0L).when(userService).count(any());
        when(knowledgeSpaceMapper.selectOne(any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> userService.register(dto));
    }
}
