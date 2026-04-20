package com.example.ragbackend.service;

import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.constant.SpaceRoleRequestConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.SpaceRoleRequest;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.model.dto.SpaceRoleRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceRoleRequestReviewDTO;
import com.example.ragbackend.service.impl.SpaceRoleRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceRoleRequestServiceImplTest {

    @Mock
    private SpaceMemberMapper spaceMemberMapper;

    @Mock
    private KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Spy
    @InjectMocks
    private SpaceRoleRequestServiceImpl spaceRoleRequestService;

    @Test
    void createRequestShouldRequireMembership() {
        SpaceRoleRequestCreateDTO dto = new SpaceRoleRequestCreateDTO();
        dto.setSpaceId(10L);
        dto.setTargetRole(SpaceJoinRequestConstants.MEMBER_ROLE);

        when(knowledgeSpaceMapper.selectById(10L)).thenReturn(new KnowledgeSpace());
        when(spaceMemberMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spaceRoleRequestService.createRequest(1L, false, dto));

        assertEquals(400, exception.getCode());
    }

    @Test
    void createRequestShouldAllowViewToApplyForAdmin() {
        SpaceRoleRequestCreateDTO dto = new SpaceRoleRequestCreateDTO();
        dto.setSpaceId(10L);
        dto.setTargetRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        when(knowledgeSpaceMapper.selectById(10L)).thenReturn(new KnowledgeSpace());

        SpaceMember member = new SpaceMember();
        member.setSpaceId(10L);
        member.setUserId(2L);
        member.setRole(SpaceJoinRequestConstants.VIEW_ROLE);
        when(spaceMemberMapper.selectOne(any())).thenReturn(member);
        doReturn(null).when(spaceRoleRequestService).getOne(any());
        doReturn(true).when(spaceRoleRequestService).save(any(SpaceRoleRequest.class));

        SpaceRoleRequest request = spaceRoleRequestService.createRequest(2L, false, dto);

        assertEquals(SpaceJoinRequestConstants.ADMIN_ROLE, request.getTargetRole());
        assertEquals(SpaceRoleRequestConstants.TYPE_ROLE_UPGRADE, request.getRequestType());
    }

    @Test
    void approveRequestShouldUpgradeMembershipRole() {
        SpaceRoleRequest request = new SpaceRoleRequest();
        request.setId(1L);
        request.setSpaceId(10L);
        request.setUserId(2L);
        request.setStatus(SpaceRoleRequestConstants.STATUS_PENDING);
        request.setTargetRole(SpaceJoinRequestConstants.MEMBER_ROLE);

        SpaceMember member = new SpaceMember();
        member.setId(99L);
        member.setSpaceId(10L);
        member.setUserId(2L);
        member.setRole(SpaceJoinRequestConstants.VIEW_ROLE);

        doReturn(request).when(spaceRoleRequestService).getById(1L);
        when(spaceMemberMapper.selectOne(any())).thenReturn(member);
        doReturn(true).when(spaceRoleRequestService).updateById(any(SpaceRoleRequest.class));

        SpaceRoleRequest approved = spaceRoleRequestService.approveRequest(1L, 100L, true, new SpaceRoleRequestReviewDTO());

        assertEquals(SpaceRoleRequestConstants.STATUS_APPROVED, approved.getStatus());
        assertEquals(SpaceJoinRequestConstants.MEMBER_ROLE, member.getRole());
        verify(spaceMemberMapper).updateById(member);
    }

    @Test
    void approveRequestShouldRejectSpaceAdminGrantingAdminRole() {
        SpaceRoleRequest request = new SpaceRoleRequest();
        request.setId(1L);
        request.setSpaceId(10L);
        request.setUserId(2L);
        request.setStatus(SpaceRoleRequestConstants.STATUS_PENDING);
        request.setTargetRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        SpaceMember reviewerMembership = new SpaceMember();
        reviewerMembership.setSpaceId(10L);
        reviewerMembership.setUserId(5L);
        reviewerMembership.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        doReturn(request).when(spaceRoleRequestService).getById(1L);
        when(spaceMemberMapper.selectOne(any())).thenReturn(reviewerMembership, reviewerMembership);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spaceRoleRequestService.approveRequest(1L, 5L, false, new SpaceRoleRequestReviewDTO()));

        assertEquals(403, exception.getCode());
    }
}
