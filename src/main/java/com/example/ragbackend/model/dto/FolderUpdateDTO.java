package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class FolderUpdateDTO {

    private String name;

    private Long parentId;
}
