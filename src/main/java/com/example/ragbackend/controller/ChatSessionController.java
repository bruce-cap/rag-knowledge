package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatSessionEntity;
import com.example.ragbackend.model.dto.ChatSessionTitleUpdateDTO;
import com.example.ragbackend.service.ChatSessionService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    @Autowired
    private ChatSessionService sessionService;

    @PostMapping("/create")
    public Result<Long> createChatSession() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Create chat session, userId={}", userId);
        return Result.success(sessionService.createSession(userId));
    }

    @GetMapping("/list")
    public Result<List<ChatSessionEntity>> listChatSession() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(sessionService.getUserSessions(userId));
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> deleteChatSession(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean success = sessionService.deleteSession(id, userId);
        return success ? Result.success("删除成功") : Result.error("删除失败或无权限");
    }

    @PutMapping("/updateTitle/{id}")
    public Result<ChatSessionEntity> updateTitle(@PathVariable Long id, @RequestBody ChatSessionTitleUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(sessionService.updateSessionTitle(id, userId, dto == null ? null : dto.getTitle()));
    }

    @PutMapping("/pin/{id}")
    public Result<ChatSessionEntity> pinSession(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(sessionService.pinSession(id, userId));
    }

    @PutMapping("/unpin/{id}")
    public Result<ChatSessionEntity> unpinSession(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(sessionService.unpinSession(id, userId));
    }

    @GetMapping("/exportMd/{id}")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String markdown = sessionService.exportSessionMarkdown(id, userId);
        String fileName = "session-" + id + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }
}
