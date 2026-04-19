package com.example.ragbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private Long folderId;

    private Long userId;

    private String fileName;

    private String minioPath;

    private Long fileSize;

    private String fileType;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean isDeleted;

    private LocalDateTime deleteTime;

    private String errorMessage;
}
