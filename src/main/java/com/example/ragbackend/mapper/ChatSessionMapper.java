package com.example.ragbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ragbackend.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
