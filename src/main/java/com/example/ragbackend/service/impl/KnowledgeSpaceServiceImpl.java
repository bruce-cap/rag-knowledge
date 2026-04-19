package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.constant.SystemSpaceConstants;
import com.example.ragbackend.entity.Document;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.DocumentMapper;
import com.example.ragbackend.mapper.FolderMapper;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.UserMapper;
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

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<KnowledgeSpace> listAccessibleSpaces(Long userId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
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
    public List<KnowledgeSpace> listAllSpaces(Long userId, boolean isSuperAdmin) {
        LambdaQueryWrapper<KnowledgeSpace> queryWrapper = new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getStatus, 1)
                .eq(KnowledgeSpace::getIsSystem, false)
                .orderByDesc(KnowledgeSpace::getCreateTime);

        if (isSuperAdmin) {
            return this.list(queryWrapper);
        }

        List<Long> joinedSpaceIds = spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                        .eq(SpaceMember::getUserId, userId))
                .stream()
                .map(SpaceMember::getSpaceId)
                .distinct()
                .collect(Collectors.toList());

        if (!joinedSpaceIds.isEmpty()) {
            queryWrapper.notIn(KnowledgeSpace::getId, joinedSpaceIds);
        }
        return this.list(queryWrapper);
    }

    @Override
    public KnowledgeSpace createSpace(Long userId, boolean isSuperAdmin, KnowledgeSpaceCreateDTO dto) {
        ensureSuperAdmin(isSuperAdmin);
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
        creatorMembership.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);
        creatorMembership.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(creatorMembership);

        return space;
    }

    @Override
    public KnowledgeSpace updateSpace(Long spaceId, Long userId, boolean isSuperAdmin, KnowledgeSpaceUpdateDTO dto) {
        ensureSuperAdmin(isSuperAdmin);
        KnowledgeSpace space = getRequiredSpace(spaceId);

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
    public void deleteSpace(Long spaceId, Long userId, boolean isSuperAdmin) {
        ensureSuperAdmin(isSuperAdmin);
        KnowledgeSpace space = getRequiredSpace(spaceId);
        if (Boolean.TRUE.equals(space.getIsSystem())) {
            throw new BusinessException(400, "System knowledge spaces cannot be deleted");
        }

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
    public List<SpaceMember> listMembers(Long spaceId, Long userId, boolean isSuperAdmin) {
        ensureSpaceViewPermission(spaceId, userId, isSuperAdmin);
        return spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .orderByAsc(SpaceMember::getJoinTime));
    }

    @Override
    public SpaceMember addMember(Long spaceId, Long operatorId, boolean isSuperAdmin, SpaceMemberAddDTO dto) {
        ensureInvitePermission(spaceId, operatorId, isSuperAdmin);
        if (dto.getUserId() == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }

        User user = getRequiredUser(dto.getUserId());
        if (!"USER".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(400, "Only ordinary users can be invited to a space");
        }

        SpaceMember existing = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, dto.getUserId())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(400, "The user is already a member of this space");
        }

        SpaceMember member = new SpaceMember();
        member.setSpaceId(spaceId);
        member.setUserId(dto.getUserId());
        member.setRole(SpaceJoinRequestConstants.MEMBER_ROLE);
        member.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(member);
        return member;
    }

    @Override
    public void removeMember(Long spaceId, Long memberUserId, Long operatorId, boolean isSuperAdmin) {
        KnowledgeSpace space = getRequiredSpace(spaceId);
        ensureSpaceManagePermission(spaceId, operatorId, isSuperAdmin);
        if (memberUserId == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }
        if (Boolean.TRUE.equals(space.getIsSystem())) {
            throw new BusinessException(400, "Members cannot be removed from system knowledge spaces");
        }

        SpaceMember targetMember = getRequiredMember(spaceId, memberUserId);
        if (!isSuperAdmin && SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(targetMember.getRole())) {
            throw new BusinessException(403, "Only super admin can remove a space admin");
        }
        if (memberUserId.equals(operatorId) && !isSuperAdmin) {
            throw new BusinessException(400, "Space admin cannot remove themselves directly");
        }

        int deleted = spaceMemberMapper.delete(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, memberUserId));
        if (deleted == 0) {
            throw new BusinessException(404, "Membership not found");
        }
    }

    @Override
    public SpaceMember updateMemberRole(Long spaceId, Long memberUserId, Long operatorId, boolean isSuperAdmin, String role) {
        ensureSuperAdmin(isSuperAdmin);
        if (memberUserId == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }
        if (role == null || role.trim().isEmpty()) {
            throw new BusinessException(400, "Role cannot be empty");
        }

        String normalizedRole = role.trim().toUpperCase();
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equals(normalizedRole)
                && !SpaceJoinRequestConstants.MEMBER_ROLE.equals(normalizedRole)) {
            throw new BusinessException(400, "Role must be ADMIN or MEMBER");
        }

        SpaceMember member = getRequiredMember(spaceId, memberUserId);
        member.setRole(normalizedRole);
        spaceMemberMapper.updateById(member);
        return member;
    }

    private KnowledgeSpace getRequiredSpace(Long spaceId) {
        KnowledgeSpace space = this.getById(spaceId);
        if (space == null) {
            throw new BusinessException(404, "Knowledge space not found");
        }
        return space;
    }

    private User getRequiredUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "User not found");
        }
        return user;
    }

    private SpaceMember getRequiredMember(Long spaceId, Long userId) {
        SpaceMember member = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(404, "Membership not found");
        }
        return member;
    }

    private void validateSpaceName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(400, "Space name cannot be empty");
        }
    }

    private void ensureSuperAdmin(boolean isSuperAdmin) {
        if (!isSuperAdmin) {
            throw new BusinessException(403, "Only super admin can perform this operation");
        }
    }

    private void ensureSpaceViewPermission(Long spaceId, Long userId, boolean isSuperAdmin) {
        getRequiredSpace(spaceId);
        if (isSuperAdmin) {
            return;
        }
        SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException(403, "You do not have permission to access this space");
        }
    }

    private void ensureSpaceManagePermission(Long spaceId, Long userId, boolean isSuperAdmin) {
        getRequiredSpace(spaceId);
        if (isSuperAdmin) {
            return;
        }
        SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException(403, "You do not belong to this space");
        }
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(403, "Only space admins can manage this space");
        }
    }

    private void ensureInvitePermission(Long spaceId, Long userId, boolean isSuperAdmin) {
        getRequiredSpace(spaceId);
        if (isSuperAdmin) {
            return;
        }
        SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getRole, SpaceJoinRequestConstants.ADMIN_ROLE)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException(403, "Only super admins or space admins can invite users");
        }
    }
}
