package com.example.ragbackend.config;

import com.example.ragbackend.constant.SystemSpaceConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicKnowledgeSpaceInitializerTest {

    @Mock
    private KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Mock
    private UserMapper userMapper;

    @Test
    void runShouldCreatePublicSpaceUsingAdminUserId() {
        when(knowledgeSpaceMapper.selectOne(any())).thenReturn(null);

        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole("SUPER_ADMIN");
        when(userMapper.selectOne(any())).thenReturn(admin);

        PublicKnowledgeSpaceInitializer initializer = new PublicKnowledgeSpaceInitializer(knowledgeSpaceMapper, userMapper);

        initializer.run(null);

        verify(knowledgeSpaceMapper).insert(org.mockito.ArgumentMatchers.<KnowledgeSpace>argThat(space ->
                SystemSpaceConstants.PUBLIC_SPACE_CODE.equals(space.getCode())
                        && SystemSpaceConstants.PUBLIC_SPACE_NAME.equals(space.getName())
                        && Long.valueOf(1L).equals(space.getCreateBy())));
    }

    @Test
    void runShouldFailWhenAdminUserMissing() {
        when(knowledgeSpaceMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        PublicKnowledgeSpaceInitializer initializer = new PublicKnowledgeSpaceInitializer(knowledgeSpaceMapper, userMapper);

        assertThrows(IllegalStateException.class, () -> initializer.run(null));
        verify(knowledgeSpaceMapper, never()).insert(any(KnowledgeSpace.class));
    }
}
