package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.model.dto.ChatDTO;
import com.example.ragbackend.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j // 加上这个注解（需要 lombok）
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/send")
    public Result<String> sendMessage(@RequestBody ChatDTO dto) {
        log.info("收到聊天请求，内容为: {}", dto);
        try {
            String response = chatService.chat(dto.getMessage());
            log.info("AI 响应成功: {}", response);
            return Result.success(response);
        } catch (Exception e) {
            log.error("AI 响应失败，错误信息: ", e);
            return Result.error("AI 忙碌中: " + e.getMessage());
        }
    }
}