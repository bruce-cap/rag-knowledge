package com.example.ragbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class ChatRequestDTO {

    private Long sessionId;

    private String message;

    private Boolean ragMode = true;

    @JsonAlias("targetSpaceId")
    private Long spaceId;

    @JsonAlias("targetFolderId")
    private Long folderId;
}
