package com.example.ragbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_space")
public class KnowledgeSpace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Integer status;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
