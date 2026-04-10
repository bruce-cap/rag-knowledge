package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.service.UserService;
import com.example.ragbackend.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service // 必须加这个注解，Spring 才能管理它
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public Result<?> login(LoginDTO loginDTO, HttpServletResponse response) {
        // 1. 查找用户
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername()));

        // 2. 校验逻辑移到了这里
        if (user == null || !encoder.matches(loginDTO.getPassword(), user.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        // 3. 生成令牌
        String token = JwtUtils.createToken(user.getUsername(), user.getId(), user.getRole());

        // 4. 设置 HttpOnly Cookie
        Cookie cookie = new Cookie("jwt_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 开发环境用 false，生产环境用 true
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        response.addCookie(cookie);

        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("username", user.getUsername());
        map.put("userId", user.getId());
        map.put("role", user.getRole());

        return Result.success(map);
    }

    @Override
    public Result<?> logout(HttpServletResponse response) {
        // 清除名为 jwt_token 的 Cookie
        Cookie cookie = new Cookie("jwt_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 与登录处的配置保持一致
        cookie.setPath("/");
        cookie.setMaxAge(0); // 设置有效期为0以让浏览器立即删除该Cookie
        response.addCookie(cookie);

        return Result.success("成功退出登录");
    }

    @Override
    public Result<?> register(RegisterDTO registerDTO) {
        String username = registerDTO.getUsername();
        String password = registerDTO.getPassword();
        String confirmPassword = registerDTO.getConfirmPassword();
        String email = registerDTO.getEmail();

        // 1. 非空与一致性校验
        if (username == null || password == null || email == null) {
            return Result.error("所有项均为必填");
        }
        if (!password.equals(confirmPassword)) {
            return Result.error("两次输入的密码不一致");
        }

        // 2. 密码复杂度校验 (正则：8-16位，必须包含字母和数字)
        String pwdRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,16}$";
        if (!password.matches(pwdRegex)) {
            return Result.error("密码必须为8-16位字母和数字结合");
        }

        // 3. 邮箱格式校验
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailRegex)) {
            return Result.error("邮箱格式不正确");
        }

        // 4. 检查用户名和邮箱是否已存在
        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .or().eq(User::getEmail, email));
        if (count > 0) {
            return Result.error("用户名或邮箱已被占用");
        }

        // 5. 加密并存库
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setRole("USER"); // 默认角色
        user.setCreateTime(LocalDateTime.now());

        return this.save(user) ? Result.success("注册成功") : Result.error("注册失败");
    }
}
