package com.example.ragbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("folder")
public class Folder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spaceId;

    private Long parentId;

    private String name;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<Folder> children = new ArrayList<>();
}
