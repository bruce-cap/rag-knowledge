package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.constant.SpaceRoleRequestConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.SpaceRoleRequest;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.SpaceRoleRequestMapper;
import com.example.ragbackend.model.dto.SpaceRoleRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceRoleRequestReviewDTO;
import com.example.ragbackend.service.SpaceRoleRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SpaceRoleRequestServiceImpl extends ServiceImpl<SpaceRoleRequestMapper, SpaceRoleRequest> implements SpaceRoleRequestService {

    @Autowired
    private SpaceMemberMapper spaceMemberMapper;

    @Autowired
    private KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Override
    public SpaceRoleRequest createRequest(Long userId, boolean isSuperAdmin, SpaceRoleRequestCreateDTO dto) {
        if (isSuperAdmin) {
            throw new BusinessException(400, "Super admin does not need to apply for role changes");
        }
        if (dto == null || dto.getSpaceId() == null) {
            throw new BusinessException(400, "Space ID cannot be null");
        }
        String targetRole = normalizeTargetRole(dto.getTargetRole());

        KnowledgeSpace space = knowledgeSpaceMapper.selectById(dto.getSpaceId());
        if (space == null) {
            throw new BusinessException(404, "Knowledge space not found");
        }

        SpaceMember membership = getRequiredMembership(dto.getSpaceId(), userId);
        if (targetRole.equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(400, "The target role is the same as the current role");
        }
        ensureRoleUpgradeAllowed(membership.getRole(), targetRole);

        ensureNoPendingRequest(dto.getSpaceId(), userId);

        SpaceRoleRequest request = new SpaceRoleRequest();
        request.setSpaceId(dto.getSpaceId());
        request.setUserId(userId);
        request.setCurrentRole(membership.getRole().toUpperCase());
        request.setTargetRole(targetRole);
        request.setRequestType(SpaceRoleRequestConstants.TYPE_ROLE_UPGRADE);
        request.setStatus(SpaceRoleRequestConstants.STATUS_PENDING);
        request.setApplyReason(dto.getApplyReason());
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        save(request);
        log.info("Space role request created, requestId={}, userId={}, spaceId={}, targetRole={}",
                request.getId(), userId, dto.getSpaceId(), targetRole);
        return request;
    }

    @Override
    public List<SpaceRoleRequest> listMyRequests(Long userId) {
        return list(new LambdaQueryWrapper<SpaceRoleRequest>()
                .eq(SpaceRoleRequest::getUserId, userId)
                .orderByDesc(SpaceRoleRequest::getCreateTime));
    }

    @Override
    public List<SpaceRoleRequest> listRequests(Long userId, boolean isSuperAdmin, Long spaceId) {
        LambdaQueryWrapper<SpaceRoleRequest> queryWrapper = new LambdaQueryWrapper<SpaceRoleRequest>()
                .orderByDesc(SpaceRoleRequest::getCreateTime);
        if (spaceId != null) {
            queryWrapper.eq(SpaceRoleRequest::getSpaceId, spaceId);
        }

        if (isSuperAdmin) {
            return list(queryWrapper);
        }

        List<Long> managedSpaceIds = listManagedSpaceIds(userId);
        if (managedSpaceIds.isEmpty()) {
            return List.of();
        }
        if (spaceId != null && !managedSpaceIds.contains(spaceId)) {
            throw new BusinessException(403, "You do not have permission to view role requests for this space");
        }

        queryWrapper.in(SpaceRoleRequest::getSpaceId, managedSpaceIds);
        return list(queryWrapper);
    }

    @Override
    public SpaceRoleRequest approveRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceRoleRequestReviewDTO dto) {
        SpaceRoleRequest request = getRequiredRequest(requestId);
        ensureReviewPermission(request.getSpaceId(), userId, isSuperAdmin);
        ensurePendingRequest(request);
        ensureReviewerCanGrantRole(request.getTargetRole(), request.getSpaceId(), userId, isSuperAdmin);

        SpaceMember membership = getRequiredMembership(request.getSpaceId(), request.getUserId());
        membership.setRole(request.getTargetRole());
        spaceMemberMapper.updateById(membership);

        request.setStatus(SpaceRoleRequestConstants.STATUS_APPROVED);
        request.setReviewReason(dto == null ? null : dto.getReviewReason());
        request.setReviewBy(userId);
        request.setReviewTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        updateById(request);
        log.info("Space role request approved, requestId={}, reviewerId={}, targetUserId={}, spaceId={}, targetRole={}",
                requestId, userId, request.getUserId(), request.getSpaceId(), request.getTargetRole());
        return request;
    }

    @Override
    public SpaceRoleRequest rejectRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceRoleRequestReviewDTO dto) {
        SpaceRoleRequest request = getRequiredRequest(requestId);
        ensureReviewPermission(request.getSpaceId(), userId, isSuperAdmin);
        ensurePendingRequest(request);

        request.setStatus(SpaceRoleRequestConstants.STATUS_REJECTED);
        request.setReviewReason(dto == null ? null : dto.getReviewReason());
        request.setReviewBy(userId);
        request.setReviewTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        updateById(request);
        log.info("Space role request rejected, requestId={}, reviewerId={}, targetUserId={}, spaceId={}",
                requestId, userId, request.getUserId(), request.getSpaceId());
        return request;
    }

    private SpaceRoleRequest getRequiredRequest(Long requestId) {
        SpaceRoleRequest request = getById(requestId);
        if (request == null) {
            throw new BusinessException(404, "Role request not found");
        }
        return request;
    }

    private SpaceMember getRequiredMembership(Long spaceId, Long userId) {
        SpaceMember membership = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
        if (membership == null) {
            throw new BusinessException(400, "The user is not a member of this space");
        }
        return membership;
    }

    private void ensureNoPendingRequest(Long spaceId, Long userId) {
        SpaceRoleRequest pendingRequest = getOne(new LambdaQueryWrapper<SpaceRoleRequest>()
                .eq(SpaceRoleRequest::getSpaceId, spaceId)
                .eq(SpaceRoleRequest::getUserId, userId)
                .eq(SpaceRoleRequest::getStatus, SpaceRoleRequestConstants.STATUS_PENDING)
                .last("LIMIT 1"));
        if (pendingRequest != null) {
            throw new BusinessException(400, "A pending role request already exists for this space");
        }
    }

    private void ensurePendingRequest(SpaceRoleRequest request) {
        if (!SpaceRoleRequestConstants.STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
            throw new BusinessException(400, "Only pending requests can be reviewed");
        }
    }

    private void ensureReviewPermission(Long spaceId, Long userId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        SpaceMember membership = getMembership(spaceId, userId);
        if (membership == null) {
            throw new BusinessException(403, "Only super admins or space admins can review role requests");
        }
        if (!SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(membership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can review role requests");
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

    private String normalizeTargetRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return SpaceJoinRequestConstants.MEMBER_ROLE;
        }
        String normalized = role.trim().toUpperCase();
        if (!SpaceJoinRequestConstants.VIEW_ROLE.equals(normalized)
                && !SpaceJoinRequestConstants.MEMBER_ROLE.equals(normalized)
                && !SpaceJoinRequestConstants.ADMIN_ROLE.equals(normalized)) {
            throw new BusinessException(400, "The target role must be VIEW, MEMBER or ADMIN");
        }
        return normalized;
    }

    private void ensureRoleUpgradeAllowed(String currentRole, String targetRole) {
        String normalizedCurrentRole = normalizeTargetRole(currentRole);
        if (SpaceJoinRequestConstants.ADMIN_ROLE.equals(normalizedCurrentRole)) {
            throw new BusinessException(400, "Space admins do not need to apply for another space role");
        }
        if (SpaceJoinRequestConstants.VIEW_ROLE.equals(normalizedCurrentRole)
                && (SpaceJoinRequestConstants.MEMBER_ROLE.equals(targetRole)
                || SpaceJoinRequestConstants.ADMIN_ROLE.equals(targetRole))) {
            return;
        }
        if (SpaceJoinRequestConstants.MEMBER_ROLE.equals(normalizedCurrentRole)
                && SpaceJoinRequestConstants.ADMIN_ROLE.equals(targetRole)) {
            return;
        }
        throw new BusinessException(400, "The requested role upgrade is not allowed");
    }

    private SpaceMember getMembership(Long spaceId, Long userId) {
        return spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private void ensureReviewerCanGrantRole(String targetRole, Long spaceId, Long userId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        SpaceMember reviewerMembership = getMembership(spaceId, userId);
        if (reviewerMembership == null || !SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(reviewerMembership.getRole())) {
            throw new BusinessException(403, "Only super admins or space admins can review role requests");
        }
        if (SpaceJoinRequestConstants.ADMIN_ROLE.equalsIgnoreCase(targetRole)) {
            throw new BusinessException(403, "Space admins cannot approve ADMIN role requests");
        }
    }
}
