package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.constant.SystemSpaceConstants;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.KnowledgeSpaceUpdateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;
import com.example.ragbackend.service.KnowledgeSpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeSpaceServiceImpl extends ServiceImpl<KnowledgeSpaceMapper, KnowledgeSpace> implements KnowledgeSpaceService {

    @Autowired
    private SpaceMemberMapper spaceMemberMapper;

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Override
    public List<KnowledgeSpace> listAccessibleSpaces(Long userId, boolean isAdmin) {
        if (isAdmin) {
            return this.list(new LambdaQueryWrapper<KnowledgeSpace>().orderByDesc(KnowledgeSpace::getCreateTime));
        }

        List<SpaceMember> memberships = spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getUserId, userId));
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> spaceIds = memberships.stream().map(SpaceMember::getSpaceId).distinct().collect(Collectors.toList());
        return this.list(new LambdaQueryWrapper<KnowledgeSpace>()
                .in(KnowledgeSpace::getId, spaceIds)
                .orderByDesc(KnowledgeSpace::getCreateTime));
    }

    @Override
    public KnowledgeSpace createSpace(Long userId, KnowledgeSpaceCreateDTO dto) {
        validateSpaceName(dto.getName());

        KnowledgeSpace space = new KnowledgeSpace();
        space.setName(dto.getName().trim());
        space.setCode(null);
        space.setType(SystemSpaceConstants.BUSINESS_SPACE_TYPE);
        space.setIsSystem(false);
        space.setDescription(dto.getDescription());
        space.setStatus(1);
        space.setCreateBy(userId);
        space.setCreateTime(LocalDateTime.now());
        space.setUpdateTime(LocalDateTime.now());
        this.save(space);

        SpaceMember creatorMembership = new SpaceMember();
        creatorMembership.setSpaceId(space.getId());
        creatorMembership.setUserId(userId);
        creatorMembership.setRole("ADMIN");
        creatorMembership.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(creatorMembership);

        return space;
    }

    @Override
    public KnowledgeSpace updateSpace(Long spaceId, Long userId, boolean isAdmin, KnowledgeSpaceUpdateDTO dto) {
        KnowledgeSpace space = getRequiredSpace(spaceId);
        ensureSpaceManagePermission(spaceId, userId, isAdmin);

        if (dto.getName() != null) {
            validateSpaceName(dto.getName());
            space.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            space.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            space.setStatus(dto.getStatus());
        }
        space.setUpdateTime(LocalDateTime.now());
        this.updateById(space);
        return space;
    }

    @Override
    public void deleteSpace(Long spaceId, Long userId, boolean isAdmin) {
        KnowledgeSpace space = getRequiredSpace(spaceId);
        if (Boolean.TRUE.equals(space.getIsSystem())) {
            throw new BusinessException(400, "System knowledge spaces cannot be deleted");
        }
        ensureSpaceManagePermission(spaceId, userId, isAdmin);

        long folderCount = folderMapper.selectCount(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getSpaceId, spaceId));
        if (folderCount > 0) {
            throw new BusinessException(400, "Cannot delete a space that still contains folders");
        }

        long documentCount = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                .eq(Document::getSpaceId, spaceId));
        if (documentCount > 0) {
            throw new BusinessException(400, "Cannot delete a space that still contains documents");
        }

        spaceMemberMapper.delete(new LambdaQueryWrapper<SpaceMember>().eq(SpaceMember::getSpaceId, spaceId));
        this.removeById(spaceId);
    }

    @Override
    public List<SpaceMember> listMembers(Long spaceId, Long userId, boolean isAdmin) {
        ensureSpaceViewPermission(spaceId, userId, isAdmin);
        return spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .orderByAsc(SpaceMember::getJoinTime));
    }

    @Override
    public SpaceMember addMember(Long spaceId, Long operatorId, boolean isAdmin, SpaceMemberAddDTO dto) {
        ensureSpaceManagePermission(spaceId, operatorId, isAdmin);
        if (dto.getUserId() == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }

        String role = dto.getRole() == null || dto.getRole().trim().isEmpty() ? "MEMBER" : dto.getRole().trim().toUpperCase();
        SpaceMember existing = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, dto.getUserId()));
        if (existing != null) {
            throw new BusinessException(400, "The user is already a member of this space");
        }

        SpaceMember member = new SpaceMember();
        member.setSpaceId(spaceId);
        member.setUserId(dto.getUserId());
        member.setRole(role);
        member.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(member);
        return member;
    }

    @Override
    public void removeMember(Long spaceId, Long memberUserId, Long operatorId, boolean isAdmin) {
        KnowledgeSpace space = getRequiredSpace(spaceId);
        ensureSpaceManagePermission(spaceId, operatorId, isAdmin);
        if (memberUserId == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }
        if (memberUserId.equals(operatorId) && !isAdmin) {
            throw new BusinessException(400, "Space admin cannot remove themselves directly");
        }
        if (Boolean.TRUE.equals(space.getIsSystem())) {
            throw new BusinessException(400, "Members cannot be removed from system knowledge spaces");
        }

        int deleted = spaceMemberMapper.delete(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, memberUserId));
        if (deleted == 0) {
            throw new BusinessException(404, "Membership not found");
        }
    }

    private KnowledgeSpace getRequiredSpace(Long spaceId) {
        KnowledgeSpace space = this.getById(spaceId);
        if (space == null) {
            throw new BusinessException(404, "Knowledge space not found");
        }
        return space;
    }

    private void validateSpaceName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(400, "Space name cannot be empty");
        }
    }

    private void ensureSpaceViewPermission(Long spaceId, Long userId, boolean isAdmin) {
        getRequiredSpace(spaceId);
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
        getRequiredSpace(spaceId);
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
            throw new BusinessException(403, "Only space admins can manage this space");
        }
    }
}
