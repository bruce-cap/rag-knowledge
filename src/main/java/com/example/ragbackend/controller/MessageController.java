package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.model.dto.MessageEditDTO;
import com.example.ragbackend.service.ChatMessageService;
import com.example.ragbackend.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/list/{sessionId}")
    public Result<List<ChatMessageEntity>> listMessages(@PathVariable("sessionId") Long sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("List messages request, userId={}, sessionId={}", userId, sessionId);
        return Result.success(chatMessageService.listSessionMessages(sessionId, userId));
    }

    //弃用
    @Deprecated
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteMessage(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Delete message request, userId={}, messageId={}", userId, id);
        chatMessageService.deleteOwnedMessage(id, userId);
        return Result.success("Message deleted successfully");
    }

    @PutMapping("/edit/{id}")
    public Result<String> editMessage(@PathVariable("id") Long id, @RequestBody MessageEditDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Edit message request, userId={}, messageId={}", userId, id);
        chatMessageService.editOwnedMessage(id, userId, dto.getContent());
        return Result.success("Message updated successfully");
    }
}
