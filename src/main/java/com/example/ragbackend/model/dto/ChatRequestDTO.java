package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class ChatRequestDTO {

    private Long sessionId;

    private String message;

    private Boolean ragMode = true;

    private Long spaceId;

    private Long folderId;
}
