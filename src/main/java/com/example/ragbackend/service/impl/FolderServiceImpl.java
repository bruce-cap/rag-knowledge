package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.model.dto.FolderCreateDTO;
import com.example.ragbackend.model.dto.FolderUpdateDTO;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FolderServiceImpl extends ServiceImpl<FolderMapper, Folder> implements FolderService {

    @Autowired
    private SpaceMemberMapper spaceMemberMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentService documentService;

    @Override
    public List<Folder> getFolderTree(Long spaceId, Long userId, boolean isAdmin) {
        ensureSpaceViewPermission(spaceId, userId, isAdmin);
        List<Folder> folders = this.list(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getSpaceId, spaceId)
                .orderByAsc(Folder::getCreateTime));
        return buildTree(folders);
    }

    @Override
    public Folder createFolder(Long userId, boolean isAdmin, FolderCreateDTO dto) {
        if (dto.getSpaceId() == null) {
            throw new BusinessException(400, "Space ID cannot be null");
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new BusinessException(400, "Folder name cannot be empty");
        }
        ensureSpaceManagePermission(dto.getSpaceId(), userId, isAdmin);
        if (dto.getParentId() != null) {
            Folder parent = getRequiredFolder(dto.getParentId());
            if (!dto.getSpaceId().equals(parent.getSpaceId())) {
                throw new BusinessException(400, "Parent folder must belong to the same space");
            }
        }

        Folder folder = new Folder();
        folder.setSpaceId(dto.getSpaceId());
        folder.setParentId(dto.getParentId());
        folder.setName(dto.getName().trim());
        folder.setCreateBy(userId);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        this.save(folder);
        return folder;
    }

    @Override
    public Folder updateFolder(Long folderId, Long userId, boolean isAdmin, FolderUpdateDTO dto) {
        Folder folder = getRequiredFolder(folderId);
        ensureSpaceManagePermission(folder.getSpaceId(), userId, isAdmin);

        if (dto.getName() != null) {
            if (dto.getName().trim().isEmpty()) {
                throw new BusinessException(400, "Folder name cannot be empty");
            }
            folder.setName(dto.getName().trim());
        }
        if (dto.getParentId() != null) {
            if (folderId.equals(dto.getParentId())) {
                throw new BusinessException(400, "Folder cannot be its own parent");
            }
            Folder parent = getRequiredFolder(dto.getParentId());
            if (!folder.getSpaceId().equals(parent.getSpaceId())) {
                throw new BusinessException(400, "Parent folder must belong to the same space");
            }
            folder.setParentId(dto.getParentId());
        }
        folder.setUpdateTime(LocalDateTime.now());
        this.updateById(folder);
        return folder;
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId, Long userId, boolean isAdmin) {
        Folder folder = getRequiredFolder(folderId);
        ensureSpaceManagePermission(folder.getSpaceId(), userId, isAdmin);

        List<Folder> allFoldersInSpace = this.list(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getSpaceId, folder.getSpaceId())
                .orderByAsc(Folder::getCreateTime));
        List<Folder> foldersToDelete = collectFoldersForDeletion(folderId, allFoldersInSpace);
        List<Long> folderIds = foldersToDelete.stream().map(Folder::getId).collect(Collectors.toList());
        documentService.purgeDocumentsByFolderIds(folderIds);

        for (Folder current : foldersToDelete) {
            this.removeById(current.getId());
        }
    }

    private Folder getRequiredFolder(Long folderId) {
        Folder folder = this.getById(folderId);
        if (folder == null) {
            throw new BusinessException(404, "Folder not found");
        }
        return folder;
    }

    private void ensureSpaceViewPermission(Long spaceId, Long userId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId));
        if (membership == null) {
            throw new BusinessException(403, "You do not have permission to access this space");
        }
    }

    private void ensureSpaceManagePermission(Long spaceId, Long userId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId));
        if (membership == null) {
            throw new BusinessException(403, "You do not belong to this space");
        }
        if (!"ADMIN".equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(403, "Only space admins can manage folders");
        }
    }

    private List<Folder> buildTree(List<Folder> folders) {
        Map<Long, Folder> folderMap = folders.stream().collect(Collectors.toMap(Folder::getId, Function.identity()));
        List<Folder> roots = new ArrayList<>();
        for (Folder folder : folders) {
            folder.setChildren(new ArrayList<>());
        }
        for (Folder folder : folders) {
            if (folder.getParentId() == null) {
                roots.add(folder);
                continue;
            }
            Folder parent = folderMap.get(folder.getParentId());
            if (parent == null) {
                roots.add(folder);
                continue;
            }
            parent.getChildren().add(folder);
        }
        return roots;
    }

    private List<Folder> collectFoldersForDeletion(Long rootFolderId, List<Folder> allFolders) {
        Map<Long, List<Folder>> childrenMap = allFolders.stream()
                .filter(folder -> folder.getParentId() != null)
                .collect(Collectors.groupingBy(Folder::getParentId));
        Map<Long, Folder> folderMap = allFolders.stream()
                .collect(Collectors.toMap(Folder::getId, Function.identity()));
        Folder root = folderMap.get(rootFolderId);
        if (root == null) {
            throw new BusinessException(404, "Folder not found");
        }

        List<Folder> ordered = new ArrayList<>();
        collectFolderPostOrder(root, childrenMap, ordered);
        return ordered;
    }

    private void collectFolderPostOrder(Folder folder, Map<Long, List<Folder>> childrenMap, List<Folder> ordered) {
        for (Folder child : childrenMap.getOrDefault(folder.getId(), List.of())) {
            collectFolderPostOrder(child, childrenMap, ordered);
        }
        ordered.add(folder);
    }
}
