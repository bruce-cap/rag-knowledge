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
import com.example.ragbackend.mapper.SpaceJoinRequestMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.SpaceRoleRequestMapper;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.KnowledgeSpaceUpdateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.service.DocumentService;
import com.example.ragbackend.service.KnowledgeSpaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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

    @Autowired
    private SpaceJoinRequestMapper spaceJoinRequestMapper;

    @Autowired
    private SpaceRoleRequestMapper spaceRoleRequestMapper;

    @Autowired
    private DocumentService documentService;

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
        log.info("Creating knowledge space, userId={}, name={}", userId, dto.getName());
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

        log.info("Knowledge space created, spaceId={}, userId={}", space.getId(), userId);
        return space;
    }

    @Override
    public KnowledgeSpace updateSpace(Long spaceId, Long userId, boolean isSuperAdmin, KnowledgeSpaceUpdateDTO dto) {
        log.info("Updating knowledge space, spaceId={}, userId={}", spaceId, userId);
        KnowledgeSpace space = getRequiredSpace(spaceId);
        ensureSpaceManagePermission(spaceId, userId, isSuperAdmin);

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
    @Transactional
    public void deleteSpace(Long spaceId, Long userId, boolean isSuperAdmin) {
        log.info("Deleting knowledge space, spaceId={}, userId={}", spaceId, userId);
        ensureSuperAdmin(isSuperAdmin);
        KnowledgeSpace space = getRequiredSpace(spaceId);
        if (Boolean.TRUE.equals(space.getIsSystem())) {
            throw new BusinessException(400, "System knowledge spaces cannot be deleted");
        }

        documentService.purgeDocumentsBySpaceId(spaceId);

        List<Folder> folders = folderMapper.selectList(new LambdaQueryWrapper<Folder>()
                .eq(Folder::getSpaceId, spaceId)
                .orderByAsc(Folder::getCreateTime));
        List<Folder> orderedFolders = orderFoldersForDeletion(folders);
        for (Folder folder : orderedFolders) {
            folderMapper.deleteById(folder.getId());
        }

        spaceJoinRequestMapper.delete(new LambdaQueryWrapper<com.example.ragbackend.entity.SpaceJoinRequest>()
                .eq(com.example.ragbackend.entity.SpaceJoinRequest::getSpaceId, spaceId));
        spaceRoleRequestMapper.delete(new LambdaQueryWrapper<com.example.ragbackend.entity.SpaceRoleRequest>()
                .eq(com.example.ragbackend.entity.SpaceRoleRequest::getSpaceId, spaceId));
        spaceMemberMapper.delete(new LambdaQueryWrapper<SpaceMember>().eq(SpaceMember::getSpaceId, spaceId));
        this.removeById(spaceId);
        log.info("Knowledge space deleted, spaceId={}, userId={}", spaceId, userId);
    }

    @Override
    public List<SpaceMember> listMembers(Long spaceId, Long userId, boolean isSuperAdmin) {
        ensureSpaceViewPermission(spaceId, userId, isSuperAdmin);
        List<SpaceMember> members = spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .orderByAsc(SpaceMember::getJoinTime));
        enrichMembers(members);
        return members;
    }

    @Override
    public List<UserListItemVO> listInvitableUsers(Long spaceId, Long userId, boolean isSuperAdmin) {
        ensureInvitePermission(spaceId, userId, isSuperAdmin);

        Set<Long> memberIds = spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                        .eq(SpaceMember::getSpaceId, spaceId))
                .stream()
                .map(SpaceMember::getUserId)
                .collect(Collectors.toSet());

        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "USER")
                        .eq(User::getStatus, 1)
                        .orderByDesc(User::getCreateTime))
                .stream()
                .filter(user -> !memberIds.contains(user.getId()))
                .map(this::toUserListItem)
                .collect(Collectors.toList());
    }

    @Override
    public SpaceMember addMember(Long spaceId, Long operatorId, boolean isSuperAdmin, SpaceMemberAddDTO dto) {
        ensureInvitePermission(spaceId, operatorId, isSuperAdmin);
        if (dto.getUserId() == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }
        String targetRole = normalizeRole(dto.getRole(), SpaceJoinRequestConstants.VIEW_ROLE);
        ensureOperatorCanAssignRole(spaceId, operatorId, isSuperAdmin, targetRole);

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
        member.setRole(targetRole);
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
        if (memberUserId == null) {
            throw new BusinessException(400, "User ID cannot be null");
        }
        String normalizedRole = normalizeRole(role, null);
        SpaceMember member = getRequiredMember(spaceId, memberUserId);
        ensureRoleAdjustmentPermission(spaceId, operatorId, isSuperAdmin, member, normalizedRole);
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

    private void enrichMembers(List<SpaceMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userMapper.selectBatchIds(members.stream()
                        .map(SpaceMember::getUserId)
                        .distinct()
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        for (SpaceMember member : members) {
            User user = userMap.get(member.getUserId());
            if (user != null) {
                member.setUsername(user.getUsername());
                member.setNickname(user.getNickname());
            }
        }
    }

    private List<Folder> orderFoldersForDeletion(List<Folder> folders) {
        if (folders == null || folders.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Folder>> childrenMap = folders.stream()
                .filter(folder -> folder.getParentId() != null)
                .collect(Collectors.groupingBy(Folder::getParentId));
        List<Folder> ordered = new java.util.ArrayList<>();
        folders.stream()
                .filter(folder -> folder.getParentId() == null)
                .forEach(folder -> collectFolderPostOrder(folder, childrenMap, ordered));
        return ordered;
    }

    private void collectFolderPostOrder(Folder folder, Map<Long, List<Folder>> childrenMap, List<Folder> ordered) {
        for (Folder child : childrenMap.getOrDefault(folder.getId(), List.of())) {
            collectFolderPostOrder(child, childrenMap, ordered);
        }
        ordered.add(folder);
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
        SpaceMember membership = getMembership(spaceId, userId);
        if (membership == null) {
            throw new BusinessException(403, "Only super admins or space admins can invite users");
        }
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can invite users");
        }
    }

    private SpaceMember getMembership(Long spaceId, Long userId) {
        return spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private String normalizeRole(String role, String defaultRole) {
        if (role == null || role.trim().isEmpty()) {
            if (defaultRole == null) {
                throw new BusinessException(400, "Role cannot be empty");
            }
            return defaultRole;
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equals(normalizedRole)
                && !SpaceJoinRequestConstants.MEMBER_ROLE.equals(normalizedRole)
                && !SpaceJoinRequestConstants.VIEW_ROLE.equals(normalizedRole)) {
            throw new BusinessException(400, "Role must be ADMIN, MEMBER or VIEW");
        }
        return normalizedRole;
    }

    private void ensureOperatorCanAssignRole(Long spaceId, Long operatorId, boolean isSuperAdmin, String targetRole) {
        if (isSuperAdmin) {
            return;
        }
        SpaceMember operatorMembership = getMembership(spaceId, operatorId);
        if (operatorMembership == null || !SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(operatorMembership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can assign roles");
        }
        if (SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(targetRole)) {
            throw new BusinessException(403, "Space admins cannot assign ADMIN role directly");
        }
    }

    private void ensureRoleAdjustmentPermission(Long spaceId, Long operatorId, boolean isSuperAdmin, SpaceMember targetMember, String targetRole) {
        getRequiredSpace(spaceId);
        if (isSuperAdmin) {
            return;
        }
        SpaceMember operatorMembership = getMembership(spaceId, operatorId);
        if (operatorMembership == null) {
            throw new BusinessException(403, "You do not belong to this space");
        }
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(operatorMembership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can adjust member roles");
        }
        if (targetMember.getUserId().equals(operatorId)) {
            throw new BusinessException(400, "Space admin cannot adjust their own role");
        }
        if (SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(targetMember.getRole())) {
            throw new BusinessException(403, "Space admin cannot adjust another space admin");
        }
        if (SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(targetRole)) {
            throw new BusinessException(403, "Space admin cannot promote a member to ADMIN");
        }
    }

    private UserListItemVO toUserListItem(User user) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
