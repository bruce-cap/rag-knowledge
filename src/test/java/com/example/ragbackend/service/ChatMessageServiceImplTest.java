package com.example.ragbackend.service;

import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.mapper.ChatSessionMapper;
import com.example.ragbackend.service.impl.ChatMessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Spy
    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    private ChatSessionEntity ownedSession;

    @BeforeEach
    void setUp() {
        ownedSession = new ChatSessionEntity();
        ownedSession.setId(1L);
        ownedSession.setUserId(99L);
    }

    @Test
    void listSessionMessagesRejectsForeignSession() {
        ChatSessionEntity foreignSession = new ChatSessionEntity();
        foreignSession.setId(2L);
        foreignSession.setUserId(100L);
        when(chatSessionMapper.selectById(2L)).thenReturn(foreignSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.listSessionMessages(2L, 99L));

        assertEquals(403, exception.getCode());
    }

    @Test
    void editOwnedMessageRejectsBlankContent() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> chatMessageService.editOwnedMessage(1L, 99L, " "));

        assertEquals(400, exception.getCode());
    }

    @Test
    void deleteOwnedMessageRemovesWhenOwnershipMatches() {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(5L);
        message.setSessionId(1L);
        when(chatSessionMapper.selectById(1L)).thenReturn(ownedSession);
        doReturn(message).when(chatMessageService).getById(5L);
        doReturn(true).when(chatMessageService).removeById(5L);

        chatMessageService.deleteOwnedMessage(5L, 99L);

        verify(chatMessageService).removeById(5L);
    }
}
