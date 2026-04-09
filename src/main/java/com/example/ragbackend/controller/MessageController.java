package com.example.ragbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.ChatMessageEntity;
import com.example.ragbackend.model.dto.MessageEditDTO;
import com.example.ragbackend.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private ChatMessageService chatMessageService;

    /**
     * P0: 获取会话的所有历史消息
     */
    @GetMapping("/list/{sessionId}")
    public Result<List<ChatMessageEntity>> listMessages(@PathVariable("sessionId") Long sessionId) {
        List<ChatMessageEntity> list = chatMessageService.list(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getCreateTime)
        );
        return Result.success(list);
    }

    /**
     * P1: 删除单条消息
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteMessage(@PathVariable("id") Long id) {
        boolean removed = chatMessageService.removeById(id);
        if (removed) {
            return Result.success("删除成功");
        } else {
            return Result.error("删除失败，消息可能不存在");
        }
    }

    /**
     * P2: 编辑已发送的消息
     */
    @PutMapping("/edit/{id}")
    public Result<String> editMessage(@PathVariable("id") Long id, @RequestBody MessageEditDTO dto) {
        if (dto == null || dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.error("消息内容不能为空");
        }

        ChatMessageEntity existingMsg = chatMessageService.getById(id);
        if (existingMsg == null) {
            return Result.error("消息不存在");
        }

        existingMsg.setContent(dto.getContent());
        boolean updated = chatMessageService.updateById(existingMsg);
        if (updated) {
            return Result.success("编辑成功");
        } else {
            return Result.error("编辑失败");
        }
    }
}
