package com.example.ragbackend.controller.AuthController;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.service.UserService;
import com.example.ragbackend.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin // 允许跨域（重要！）
public class AuthController {

    @Autowired
    private UserService userService; // 注入的是接口

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginDTO loginDTO, jakarta.servlet.http.HttpServletResponse response) {
        Result<?> result = userService.login(loginDTO);
        // 登录成功后设置JWT到httpOnly cookie
        if (result.getCode() == 200 && result.getData() != null) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) result.getData();
            String token = (String) data.get("token");
            if (token != null) {
                jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt_token", token);
                cookie.setHttpOnly(true);
                cookie.setSecure(false); // 生产环境设为true
                cookie.setPath("/");
                cookie.setMaxAge(86400); // 24小时
                response.addCookie(cookie);
            }
        }
        return result;
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterDTO registerDTO) {
        return userService.register(registerDTO);
    }
}