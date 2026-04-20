package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceJoinRequest;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceJoinRequestMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.SpaceJoinRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceJoinRequestReviewDTO;
import com.example.ragbackend.service.SpaceJoinRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpaceJoinRequestServiceImpl extends ServiceImpl<SpaceJoinRequestMapper, SpaceJoinRequest> implements SpaceJoinRequestService {

    @Autowired
    private KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Autowired
    private SpaceMemberMapper spaceMemberMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public SpaceJoinRequest createRequest(Long userId, boolean isSuperAdmin, SpaceJoinRequestCreateDTO dto) {
        if (isSuperAdmin) {
            throw new BusinessException(400, "Super admin does not need to apply for space access");
        }
        if (dto == null || dto.getSpaceId() == null) {
            throw new BusinessException(400, "Space ID cannot be null");
        }

        KnowledgeSpace space = getRequiredSpace(dto.getSpaceId());
        if (Boolean.TRUE.equals(space.getIsSystem())) {
            throw new BusinessException(400, "System spaces do not support join requests");
        }
        String targetRole = normalizeTargetRole(dto.getTargetRole());

        ensureOrdinaryUser(userId);
        ensureNotMember(dto.getSpaceId(), userId);
        ensureNoPendingRequest(dto.getSpaceId(), userId);

        SpaceJoinRequest request = new SpaceJoinRequest();
        request.setSpaceId(dto.getSpaceId());
        request.setUserId(userId);
        request.setTargetRole(targetRole);
        request.setStatus(SpaceJoinRequestConstants.STATUS_PENDING);
        request.setApplyReason(dto.getApplyReason());
        request.setReviewReason(null);
        request.setReviewBy(null);
        request.setReviewTime(null);
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        this.save(request);
        log.info("Space join request created, requestId={}, userId={}, spaceId={}, targetRole={}",
                request.getId(), userId, dto.getSpaceId(), targetRole);
        return request;
    }

    @Override
    public List<SpaceJoinRequest> listMyRequests(Long userId) {
        return this.list(new LambdaQueryWrapper<SpaceJoinRequest>()
                .eq(SpaceJoinRequest::getUserId, userId)
                .orderByDesc(SpaceJoinRequest::getCreateTime));
    }

    @Override
    public List<SpaceJoinRequest> listRequests(Long userId, boolean isSuperAdmin, Long spaceId) {
        LambdaQueryWrapper<SpaceJoinRequest> queryWrapper = new LambdaQueryWrapper<SpaceJoinRequest>()
                .orderByDesc(SpaceJoinRequest::getCreateTime);

        if (spaceId != null) {
            queryWrapper.eq(SpaceJoinRequest::getSpaceId, spaceId);
        }

        if (isSuperAdmin) {
            return this.list(queryWrapper);
        }

        List<Long> managedSpaceIds = listManagedSpaceIds(userId);
        if (managedSpaceIds.isEmpty()) {
            return List.of();
        }

        if (spaceId != null && !managedSpaceIds.contains(spaceId)) {
            throw new BusinessException(403, "You do not have permission to view requests for this space");
        }

        queryWrapper.in(SpaceJoinRequest::getSpaceId, managedSpaceIds);
        return this.list(queryWrapper);
    }

    @Override
    public SpaceJoinRequest approveRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceJoinRequestReviewDTO dto) {
        SpaceJoinRequest request = getRequiredRequest(requestId);
        ensureReviewPermission(request.getSpaceId(), userId, isSuperAdmin);
        ensurePendingRequest(request);
        ensureNotMember(request.getSpaceId(), request.getUserId());
        ensureReviewerCanGrantRole(request.getTargetRole(), request.getSpaceId(), userId, isSuperAdmin);

        SpaceMember member = new SpaceMember();
        member.setSpaceId(request.getSpaceId());
        member.setUserId(request.getUserId());
        member.setRole(request.getTargetRole());
        member.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(member);

        request.setStatus(SpaceJoinRequestConstants.STATUS_APPROVED);
        request.setReviewReason(dto == null ? null : dto.getReviewReason());
        request.setReviewBy(userId);
        request.setReviewTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        this.updateById(request);
        log.info("Space join request approved, requestId={}, reviewerId={}, targetUserId={}, spaceId={}, targetRole={}",
                requestId, userId, request.getUserId(), request.getSpaceId(), request.getTargetRole());
        return request;
    }

    @Override
    public SpaceJoinRequest rejectRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceJoinRequestReviewDTO dto) {
        SpaceJoinRequest request = getRequiredRequest(requestId);
        ensureReviewPermission(request.getSpaceId(), userId, isSuperAdmin);
        ensurePendingRequest(request);

        request.setStatus(SpaceJoinRequestConstants.STATUS_REJECTED);
        request.setReviewReason(dto == null ? null : dto.getReviewReason());
        request.setReviewBy(userId);
        request.setReviewTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        this.updateById(request);
        log.info("Space join request rejected, requestId={}, reviewerId={}, targetUserId={}, spaceId={}",
                requestId, userId, request.getUserId(), request.getSpaceId());
        return request;
    }

    private KnowledgeSpace getRequiredSpace(Long spaceId) {
        KnowledgeSpace space = knowledgeSpaceMapper.selectById(spaceId);
        if (space == null) {
            throw new BusinessException(404, "Knowledge space not found");
        }
        return space;
    }

    private SpaceJoinRequest getRequiredRequest(Long requestId) {
        SpaceJoinRequest request = this.getById(requestId);
        if (request == null) {
            throw new BusinessException(404, "Join request not found");
        }
        return request;
    }

    private void ensureOrdinaryUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "User not found");
        }
        if (!"USER".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(400, "Only ordinary users can apply for space access");
        }
    }

    private void ensureNotMember(Long spaceId, Long userId) {
        SpaceMember existingMember = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
        if (existingMember != null) {
            throw new BusinessException(400, "The user is already a member of this space");
        }
    }

    private void ensureNoPendingRequest(Long spaceId, Long userId) {
        SpaceJoinRequest existingRequest = this.getOne(new LambdaQueryWrapper<SpaceJoinRequest>()
                .eq(SpaceJoinRequest::getSpaceId, spaceId)
                .eq(SpaceJoinRequest::getUserId, userId)
                .eq(SpaceJoinRequest::getStatus, SpaceJoinRequestConstants.STATUS_PENDING)
                .last("LIMIT 1"));
        if (existingRequest != null) {
            throw new BusinessException(400, "A pending join request already exists for this space");
        }
    }

    private void ensurePendingRequest(SpaceJoinRequest request) {
        if (!SpaceJoinRequestConstants.STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
            throw new BusinessException(400, "Only pending requests can be reviewed");
        }
    }

    private void ensureReviewPermission(Long spaceId, Long userId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        SpaceMember membership = getMembership(spaceId, userId);
        if (membership == null) {
            throw new BusinessException(403, "Only super admins or space admins can review join requests");
        }
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can review join requests");
        }
    }

    private List<Long> listManagedSpaceIds(Long userId) {
        return spaceMemberMapper.selectList(new LambdaQueryWrapper<SpaceMember>()
                        .eq(SpaceMember::getUserId, userId)
                        .eq(SpaceMember::getRole, SpaceJoinRequestConstants.ADMIN_ROLE))
                .stream()
                .map(SpaceMember::getSpaceId)
                .distinct()
                .collect(Collectors.toList());
    }

    private SpaceMember getMembership(Long spaceId, Long userId) {
        return spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private String normalizeTargetRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return SpaceJoinRequestConstants.VIEW_ROLE;
        }
        String normalized = role.trim().toUpperCase();
        if (!SpaceJoinRequestConstants.VIEW_ROLE.equals(normalized)
                && !SpaceJoinRequestConstants.MEMBER_ROLE.equals(normalized)
                && !SpaceJoinRequestConstants.ADMIN_ROLE.equals(normalized)) {
            throw new BusinessException(400, "The target role must be VIEW, MEMBER or ADMIN");
        }
        return normalized;
    }

    private void ensureReviewerCanGrantRole(String targetRole, Long spaceId, Long userId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        SpaceMember reviewerMembership = getMembership(spaceId, userId);
        if (reviewerMembership == null || !SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(reviewerMembership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can review join requests");
        }
        if (SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(targetRole)) {
            throw new BusinessException(403, "Space admins cannot approve join requests for ADMIN role");
        }
    }
}
