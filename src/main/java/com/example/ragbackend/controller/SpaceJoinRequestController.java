package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.SpaceJoinRequest;
import com.example.ragbackend.model.dto.SpaceJoinRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceJoinRequestReviewDTO;
import com.example.ragbackend.service.SpaceJoinRequestService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/space/request")
public class SpaceJoinRequestController {

    @Autowired
    private SpaceJoinRequestService spaceJoinRequestService;

    @PostMapping("/create")
    public Result<SpaceJoinRequest> createRequest(@RequestBody SpaceJoinRequestCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Create space join request, userId={}, spaceId={}", userId, dto == null ? null : dto.getSpaceId());
        return Result.success(spaceJoinRequestService.createRequest(userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @GetMapping("/myList")
    public Result<List<SpaceJoinRequest>> listMyRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("List my space join requests, userId={}", userId);
        return Result.success(spaceJoinRequestService.listMyRequests(userId));
    }

    @GetMapping("/list")
    public Result<List<SpaceJoinRequest>> listRequests(@RequestParam(value = "spaceId", required = false) Long spaceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("List space join requests, userId={}, spaceId={}", userId, spaceId);
        return Result.success(spaceJoinRequestService.listRequests(userId, SecurityUtils.isSuperAdmin(), spaceId));
    }

    @PutMapping("/approve/{id}")
    public Result<SpaceJoinRequest> approveRequest(@PathVariable Long id,
                                                   @RequestBody(required = false) SpaceJoinRequestReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Approve space join request, requestId={}, reviewerId={}", id, userId);
        return Result.success(spaceJoinRequestService.approveRequest(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @PutMapping("/reject/{id}")
    public Result<SpaceJoinRequest> rejectRequest(@PathVariable Long id,
                                                  @RequestBody(required = false) SpaceJoinRequestReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Reject space join request, requestId={}, reviewerId={}", id, userId);
        return Result.success(spaceJoinRequestService.rejectRequest(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }
}
