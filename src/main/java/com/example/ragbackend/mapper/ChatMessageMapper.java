package com.example.ragbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ragbackend.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
    // 如果以后需要自定义复杂的统计 SQL，可以在这里写方法并在 XML 中实现
}
