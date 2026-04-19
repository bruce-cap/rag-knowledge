package com.example.ragbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("space_member")
public class SpaceMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private Long userId;

    private String role;

    private LocalDateTime joinTime;
}
