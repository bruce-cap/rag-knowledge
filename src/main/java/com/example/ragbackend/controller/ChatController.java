package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.model.dto.ChatRequestDTO;
import com.example.ragbackend.model.vo.ChatResponseVO;
import com.example.ragbackend.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/send")
    public Result<ChatResponseVO> sendMessage(@RequestBody ChatRequestDTO request) {
        log.info("收到聊天请求，内容为: {}", request);
        try {
            ChatResponseVO response = chatService.chat(request);
            log.info("AI 响应成功: {}", response);
            return Result.success(response);
        } catch (Exception e) {
            log.error("AI 响应失败，错误信息: ", e);
            return Result.error("AI 忙碌中: " + e.getMessage());
        }
    }
}
