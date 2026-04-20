package com.example.ragbackend.service;

import com.example.ragbackend.constant.SpaceJoinRequestConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceJoinRequest;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.SpaceJoinRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceJoinRequestReviewDTO;
import com.example.ragbackend.service.impl.SpaceJoinRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceJoinRequestServiceImplTest {

    @Mock
    private KnowledgeSpaceMapper knowledgeSpaceMapper;

    @Mock
    private SpaceMemberMapper spaceMemberMapper;

    @Mock
    private UserMapper userMapper;

    @Spy
    @InjectMocks
    private SpaceJoinRequestServiceImpl spaceJoinRequestService;

    @Test
    void createRequestShouldRejectSuperAdmin() {
        SpaceJoinRequestCreateDTO dto = new SpaceJoinRequestCreateDTO();
        dto.setSpaceId(10L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spaceJoinRequestService.createRequest(1L, true, dto));

        assertEquals(400, exception.getCode());
    }

    @Test
    void createRequestShouldPersistPendingRequest() {
        SpaceJoinRequestCreateDTO dto = new SpaceJoinRequestCreateDTO();
        dto.setSpaceId(10L);
        dto.setTargetRole(SpaceJoinRequestConstants.ADMIN_ROLE);
        dto.setApplyReason("Need access to this space");

        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(10L);
        space.setIsSystem(false);
        when(knowledgeSpaceMapper.selectById(10L)).thenReturn(space);

        User user = new User();
        user.setId(2L);
        user.setRole("USER");
        when(userMapper.selectById(2L)).thenReturn(user);
        when(spaceMemberMapper.selectOne(any())).thenReturn(null);
        doReturn(null).when(spaceJoinRequestService).getOne(any());
        doReturn(true).when(spaceJoinRequestService).save(any(SpaceJoinRequest.class));

        SpaceJoinRequest request = spaceJoinRequestService.createRequest(2L, false, dto);

        assertEquals(SpaceJoinRequestConstants.STATUS_PENDING, request.getStatus());
        assertEquals(10L, request.getSpaceId());
        assertEquals(2L, request.getUserId());
        assertEquals(SpaceJoinRequestConstants.ADMIN_ROLE, request.getTargetRole());
    }

    @Test
    void approveRequestShouldAddMemberWithRequestedRole() {
        SpaceJoinRequest request = new SpaceJoinRequest();
        request.setId(1L);
        request.setSpaceId(10L);
        request.setUserId(2L);
        request.setTargetRole(SpaceJoinRequestConstants.MEMBER_ROLE);
        request.setStatus(SpaceJoinRequestConstants.STATUS_PENDING);

        doReturn(request).when(spaceJoinRequestService).getById(1L);
        when(spaceMemberMapper.selectOne(any())).thenReturn(null);
        doReturn(true).when(spaceJoinRequestService).updateById(any(SpaceJoinRequest.class));

        SpaceJoinRequestReviewDTO dto = new SpaceJoinRequestReviewDTO();
        dto.setReviewReason("approved");

        SpaceJoinRequest approved = spaceJoinRequestService.approveRequest(1L, 99L, true, dto);

        assertEquals(SpaceJoinRequestConstants.STATUS_APPROVED, approved.getStatus());
        verify(spaceMemberMapper).insert(argThat((SpaceMember member) ->
                member.getSpaceId().equals(10L)
                        && member.getUserId().equals(2L)
                        && SpaceJoinRequestConstants.MEMBER_ROLE.equals(member.getRole())));
    }

    @Test
    void approveRequestShouldRejectSpaceAdminGrantingAdminRole() {
        SpaceJoinRequest request = new SpaceJoinRequest();
        request.setId(1L);
        request.setSpaceId(10L);
        request.setUserId(2L);
        request.setTargetRole(SpaceJoinRequestConstants.ADMIN_ROLE);
        request.setStatus(SpaceJoinRequestConstants.STATUS_PENDING);

        SpaceMember reviewerMembership = new SpaceMember();
        reviewerMembership.setSpaceId(10L);
        reviewerMembership.setUserId(5L);
        reviewerMembership.setRole(SpaceJoinRequestConstants.ADMIN_ROLE);

        doReturn(request).when(spaceJoinRequestService).getById(1L);
        when(spaceMemberMapper.selectOne(any())).thenReturn(reviewerMembership, null, reviewerMembership);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> spaceJoinRequestService.approveRequest(1L, 5L, false, new SpaceJoinRequestReviewDTO()));

        assertEquals(403, exception.getCode());
    }
}
