package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class SearchRequestDTO {

    private String query;

    private Integer limit = 5;

    private Double minScore = 0.5;

    private Long spaceId;

    private Long folderId;
}
