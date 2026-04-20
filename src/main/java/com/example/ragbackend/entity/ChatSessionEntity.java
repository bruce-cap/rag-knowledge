package com.example.ragbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private Boolean isTitleManual;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    private Boolean isPinned;

    private LocalDateTime pinTime;
}
