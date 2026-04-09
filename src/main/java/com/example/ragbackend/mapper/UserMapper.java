package com.example.ragbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ragbackend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 这里继承了 BaseMapper，增删改查都已经自动写好了
}
