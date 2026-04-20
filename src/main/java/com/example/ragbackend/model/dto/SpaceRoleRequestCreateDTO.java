package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class SpaceRoleRequestCreateDTO {

    private Long spaceId;

    private String targetRole;

    private String applyReason;
}
