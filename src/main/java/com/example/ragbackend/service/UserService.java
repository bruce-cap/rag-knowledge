package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import jakarta.servlet.http.HttpServletResponse;

// 继承 IService 是 MyBatis-Plus 的增强功能，自带了很多方法
public interface UserService extends IService<User> {
    /**
     * 登录业务逻辑
     * @param loginDTO 登录参数
     * @param response HTTP响应对象，用于设置Cookie
     * @return 返回包含 Token 的结果
     */
    Result<?> login(LoginDTO loginDTO, HttpServletResponse response);

    /**
     * 退出登录业务逻辑
     * @param response HTTP响应对象，用于清除Cookie
     * @return 返回登出结果
     */
    Result<?> logout(HttpServletResponse response);





    /**
     * 注册业务逻辑
     * @param registerDTO 注册参数
     * @return 返回结果
     */
    Result<?> register(RegisterDTO registerDTO); // 新增注册接口
}
