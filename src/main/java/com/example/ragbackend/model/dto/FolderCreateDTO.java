package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class FolderCreateDTO {

    private Long spaceId;

    private Long parentId;

    private String name;
}
