package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.SpaceJoinRequest;
import com.example.ragbackend.model.dto.SpaceJoinRequestCreateDTO;
import com.example.ragbackend.model.dto.SpaceJoinRequestReviewDTO;
import com.example.ragbackend.service.SpaceJoinRequestService;
import com.example.ragbackend.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/space/request")
public class SpaceJoinRequestController {

    @Autowired
    private SpaceJoinRequestService spaceJoinRequestService;

    @PostMapping("/create")
    public Result<SpaceJoinRequest> createRequest(@RequestBody SpaceJoinRequestCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(spaceJoinRequestService.createRequest(userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @GetMapping("/myList")
    public Result<List<SpaceJoinRequest>> listMyRequests() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(spaceJoinRequestService.listMyRequests(userId));
    }

    @GetMapping("/list")
    public Result<List<SpaceJoinRequest>> listRequests(@RequestParam(value = "spaceId", required = false) Long spaceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(spaceJoinRequestService.listRequests(userId, SecurityUtils.isSuperAdmin(), spaceId));
    }

    @PutMapping("/approve/{id}")
    public Result<SpaceJoinRequest> approveRequest(@PathVariable Long id,
                                                   @RequestBody(required = false) SpaceJoinRequestReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(spaceJoinRequestService.approveRequest(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @PutMapping("/reject/{id}")
    public Result<SpaceJoinRequest> rejectRequest(@PathVariable Long id,
                                                  @RequestBody(required = false) SpaceJoinRequestReviewDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(spaceJoinRequestService.rejectRequest(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }
}
