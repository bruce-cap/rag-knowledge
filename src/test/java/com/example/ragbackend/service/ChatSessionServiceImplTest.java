package com.example.ragbackend.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.service.impl.ChatSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatTitleService chatTitleService;

    @Spy
    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    private ChatSessionEntity ownedSession;

    @BeforeEach
    void setUp() {
        ownedSession = new ChatSessionEntity();
        ownedSession.setId(1L);
        ownedSession.setUserId(99L);
        ownedSession.setIsPinned(true);
    }

    @Test
    void unpinSessionShouldExplicitlyClearPinTime() {
        doReturn(ownedSession).when(chatSessionService).getOne(any());
        doReturn(true).when(chatSessionService).update(any(Wrapper.class));

        ChatSessionEntity result = chatSessionService.unpinSession(1L, 99L);

        verify(chatSessionService).update(argThat(wrapper ->
                wrapper.getSqlSet() != null
                        && wrapper.getSqlSet().contains("is_pinned")
                        && wrapper.getSqlSet().contains("pin_time")));
        assertFalse(Boolean.TRUE.equals(result.getIsPinned()));
        assertNull(result.getPinTime());
    }
}
