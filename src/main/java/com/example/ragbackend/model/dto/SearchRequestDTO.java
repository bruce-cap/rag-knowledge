package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class SearchRequestDTO {
    /**
     * 搜索的文本内容
     */
    private String query;

    /**
     * 返回的最大结果数
     */
    private Integer limit = 5;

    /**
     * 最低相似度阈值
     */
    private Double minScore = 0.5;
}
