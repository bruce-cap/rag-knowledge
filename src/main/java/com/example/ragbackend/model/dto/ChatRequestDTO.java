package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    private String message;
    private Long sessionId; // null表示新会话
}
