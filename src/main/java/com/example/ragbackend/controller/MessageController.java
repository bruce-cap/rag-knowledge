package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.model.dto.MessageEditDTO;
import com.example.ragbackend.service.ChatMessageService;
import com.example.ragbackend.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/list/{sessionId}")
    public Result<List<ChatMessageEntity>> listMessages(@PathVariable("sessionId") Long sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(chatMessageService.listSessionMessages(sessionId, userId));
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> deleteMessage(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        chatMessageService.deleteOwnedMessage(id, userId);
        return Result.success("Message deleted successfully");
    }

    @PutMapping("/edit/{id}")
    public Result<String> editMessage(@PathVariable("id") Long id, @RequestBody MessageEditDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        chatMessageService.editOwnedMessage(id, userId, dto.getContent());
        return Result.success("Message updated successfully");
    }
}
