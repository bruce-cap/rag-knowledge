package com.example.ragbackend.service;

import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.service.impl.UserServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class UserServiceListAllUsersTest {

    @Mock
    private com.example.ragbackend.mapper.KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Mock
    private com.example.ragbackend.mapper.SpaceMemberMapper spaceMemberMapper;

    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void listAllUsersShouldRequireSuperAdmin() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.listAllUsers(false));

        assertEquals(403, exception.getCode());
    }

    @Test
    void listAllUsersShouldReturnSanitizedUsers() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("secret");
        user.setEmail("alice@example.com");
        user.setRole("USER");
        user.setCreateTime(LocalDateTime.of(2026, 4, 20, 10, 0));

        doReturn(List.of(user)).when(userService).list(org.mockito.ArgumentMatchers.<Wrapper<User>>any());

        List<UserListItemVO> result = userService.listAllUsers(true);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).getUsername());
        assertEquals("alice@example.com", result.get(0).getEmail());
        assertEquals("USER", result.get(0).getRole());
    }
}
