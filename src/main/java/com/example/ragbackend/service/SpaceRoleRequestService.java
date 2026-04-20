package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.SpaceRoleRequest;
import com.example.ragbackend.model.dto.SpaceRoleRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceRoleRequestReviewDTO;

import java.util.List;

public interface SpaceRoleRequestService extends IService<SpaceRoleRequest> {

    SpaceRoleRequest createRequest(Long userId, boolean isSuperAdmin, SpaceRoleRequestCreateDTO dto);

    List<SpaceRoleRequest> listMyRequests(Long userId);

    List<SpaceRoleRequest> listRequests(Long userId, boolean isSuperAdmin, Long spaceId);

    SpaceRoleRequest approveRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceRoleRequestReviewDTO dto);

    SpaceRoleRequest rejectRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceRoleRequestReviewDTO dto);
}
