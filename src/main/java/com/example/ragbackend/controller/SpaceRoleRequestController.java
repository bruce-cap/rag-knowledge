package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.SpaceRoleRequest;
import com.example.ragbackend.model.dto.SpaceRoleRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceRoleRequestReviewDTO;
import com.example.ragbackend.service.SpaceRoleRequestService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/space/roleRequest")
@Slf4j
public class SpaceRoleRequestController {

    @Autowired
    private SpaceRoleRequestService spaceRoleRequestService;

    @PostMapping("/create")
    public Result<SpaceRoleRequest> createRequest(@RequestBody SpaceRoleRequestCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Create space role request, userId={}, spaceId={}, targetRole={}",
                userId, dto == null ? null : dto.getSpaceId(), dto == null ? null : dto.getTargetRole());
        return Result.success(spaceRoleRequestService.createRequest(userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @GetMapping("/myList")
    public Result<List<SpaceRoleRequest>> listMyRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("List my space role requests, userId={}", userId);
        return Result.success(spaceRoleRequestService.listMyRequests(userId));
    }

    @GetMapping("/list")
    public Result<List<SpaceRoleRequest>> listRequests(@RequestParam(value = "spaceId", required = false) Long spaceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("List space role requests, userId={}, spaceId={}", userId, spaceId);
        return Result.success(spaceRoleRequestService.listRequests(userId, SecurityUtils.isSuperAdmin(), spaceId));
    }

    @PutMapping("/approve/{id}")
    public Result<SpaceRoleRequest> approveRequest(@PathVariable Long id,
                                                   @RequestBody(required = false) SpaceRoleRequestReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Approve space role request, requestId={}, reviewerId={}", id, userId);
        return Result.success(spaceRoleRequestService.approveRequest(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @PutMapping("/reject/{id}")
    public Result<SpaceRoleRequest> rejectRequest(@PathVariable Long id,
                                                  @RequestBody(required = false) SpaceRoleRequestReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Reject space role request, requestId={}, reviewerId={}", id, userId);
        return Result.success(spaceRoleRequestService.rejectRequest(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }
}
