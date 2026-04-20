package com.example.ragbackend.service;

import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.service.impl.FolderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceImplTest {

    @Mock
    private SpaceMemberMapper spaceMemberMapper;

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentService documentService;

    @Spy
    @InjectMocks
    private FolderServiceImpl folderService;

    @Test
    void deleteFolderShouldRecursivelyDeleteChildrenAndDocuments() {
        Folder root = new Folder();
        root.setId(1L);
        root.setSpaceId(10L);

        Folder child = new Folder();
        child.setId(2L);
        child.setSpaceId(10L);
        child.setParentId(1L);

        SpaceMember admin = new SpaceMember();
        admin.setSpaceId(10L);
        admin.setUserId(99L);
        admin.setRole("ADMIN");

        doReturn(root).when(folderService).getById(1L);
        doReturn(List.of(root, child)).when(folderService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        when(spaceMemberMapper.selectOne(any())).thenReturn(admin);
        doReturn(true).when(folderService).removeById(any(Serializable.class));

        folderService.deleteFolder(1L, 99L, false);

        verify(documentService).purgeDocumentsByFolderIds(List.of(2L, 1L));
        verify(folderService).removeById(2L);
        verify(folderService).removeById(1L);
    }
}
