package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.SpaceJoinRequest;
import com.example.ragbackend.model.dto.SpaceJoinRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceJoinRequestReviewDTO;

import java.util.List;

public interface SpaceJoinRequestService extends IService<SpaceJoinRequest> {

    SpaceJoinRequest createRequest(Long userId, boolean isSuperAdmin, SpaceJoinRequestCreateDTO dto);

    List<SpaceJoinRequest> listMyRequests(Long userId);

    List<SpaceJoinRequest> listRequests(Long userId, boolean isSuperAdmin, Long spaceId);

    SpaceJoinRequest approveRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceJoinRequestReviewDTO dto);

    SpaceJoinRequest rejectRequest(Long requestId, Long userId, boolean isSuperAdmin, SpaceJoinRequestReviewDTO dto);
}
