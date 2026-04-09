package com.example.ragbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatMessage;
import com.example.ragbackend.entity.ChatSession;
import com.example.ragbackend.mapper.ChatMessageMapper;
import com.example.ragbackend.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/session")
public class SessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @GetMapping
    public Result<List<ChatSession>> list() {
        List<ChatSession> sessions = chatSessionService.list(
            new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, 1L) // TODO: 从 SecurityContext 获取当前用户ID
                .orderByDesc(ChatSession::getUpdateTime)
        );
        return Result.success(sessions);
    }

    @GetMapping("/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, id)
                .orderByAsc(ChatMessage::getCreateTime)
        );
        return Result.success(messages);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        // 删除会话（逻辑删除）
        chatSessionService.removeById(id);
        return Result.success("删除成功");
    }
}
