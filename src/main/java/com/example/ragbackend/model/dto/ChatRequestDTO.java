package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {
    /**
     * 当前会话的 ID
     */
    private Long sessionId;

    /**
     * 用户输入的聊天内容
     */
    private String message;

    /**
     * 是否开启 RAG 知识库检索模式
     */
    private Boolean ragMode = true;
}