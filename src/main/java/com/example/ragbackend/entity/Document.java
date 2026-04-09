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

    private Long userId;

    private String fileName;

    private String minioPath;

    private Long fileSize;

    /**
     * 状态：0-上传成功，1-解析中，2-解析完成
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}