package com.example.ragbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseVO {
    private String content;
    private Long sessionId;
    private Long messageId; // AI回复的消息ID
}
