package com.example.ragbackend.controller;

import com.example.ragbackend.entity.User;
import com.example.ragbackend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/test")
    public String test() {
        return "✅ Spring Boot backend is running!";
    }

    //查询用户信息
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/user")
    public String getUser() {
        // 示例：查询ID为1的用户，实际业务中应根据请求参数动态获取ID
        User user = userMapper.selectById(1);
        return user != null ? user.toString() : "User not found";
    }
}