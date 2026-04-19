package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class KnowledgeSpaceUpdateDTO {

    private String name;

    private String description;

    private Integer status;
}
