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
     * 0-上传成功, 1-处理中, 2-处理完成, 3-处理失败
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
