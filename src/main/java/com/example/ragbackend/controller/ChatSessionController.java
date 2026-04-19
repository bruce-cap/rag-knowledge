package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.service.ChatSessionService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    @Autowired
    private ChatSessionService sessionService;

    @PostMapping("/create")
    public Result<Long> createSession() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 发起创建会话请求", userId);
        return Result.success(sessionService.createSession(userId));
    }

    @GetMapping("/list")
    public Result<List<ChatSessionEntity>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(sessionService.getUserSessions(userId));
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 这里的 Service 实现需要确保删除的是该用户自己的会话
        boolean success = sessionService.deleteSession(id, userId);
        return success ? Result.success("删除成功") : Result.error("删除失败或无权限");
    }
}
