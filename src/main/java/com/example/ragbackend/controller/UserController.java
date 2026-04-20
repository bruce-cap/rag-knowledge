package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.model.dto.UserPasswordUpdateDTO;
import com.example.ragbackend.model.dto.UserProfileUpdateDTO;
import com.example.ragbackend.model.dto.UserStatusUpdateDTO;
import com.example.ragbackend.model.vo.UserDetailVO;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.model.vo.UserProfileVO;
import com.example.ragbackend.service.UserService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    public Result<List<UserListItemVO>> listUsers() {
        log.info("List users request");
        return Result.success(userService.listAllUsers(SecurityUtils.isSuperAdmin()));
    }

    @GetMapping("/detail/{id}")
    public Result<UserDetailVO> getUserDetail(@PathVariable Long id) {
        log.info("Get user detail request, targetUserId={}", id);
        return Result.success(userService.getUserDetail(id, SecurityUtils.isSuperAdmin()));
    }

    @PutMapping("/updateStatus/{id}")
    public Result<UserDetailVO> updateUserStatus(@PathVariable Long id, @RequestBody UserStatusUpdateDTO dto) {
        log.info("Update user status request, targetUserId={}, status={}", id, dto == null ? null : dto.getStatus());
        return Result.success(userService.updateUserStatus(id, dto == null ? null : dto.getStatus(), SecurityUtils.isSuperAdmin()));
    }

    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Get profile request, userId={}", userId);
        return Result.success(userService.getCurrentUserProfile(userId));
    }

    @PutMapping("/updateProfile")
    public Result<UserProfileVO> updateProfile(@RequestBody UserProfileUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Update profile request, userId={}", userId);
        return Result.success(userService.updateCurrentUserProfile(userId, dto));
    }

    @PutMapping("/updatePassword")
    public Result<String> updatePassword(@RequestBody UserPasswordUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Update password request, userId={}", userId);
        userService.updateCurrentUserPassword(userId, dto);
        return Result.success("Password updated successfully");
    }
}
