package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.model.dto.FolderCreateDTO;
import com.example.ragbackend.model.dto.FolderUpdateDTO;

import java.util.List;

public interface FolderService extends IService<Folder> {

    List<Folder> getFolderTree(Long spaceId, Long userId, boolean isAdmin);

    Folder createFolder(Long userId, boolean isAdmin, FolderCreateDTO dto);

    Folder updateFolder(Long folderId, Long userId, boolean isAdmin, FolderUpdateDTO dto);

    void deleteFolder(Long folderId, Long userId, boolean isAdmin);
}
