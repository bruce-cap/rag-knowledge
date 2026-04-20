package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.model.vo.UserListItemVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface UserService extends IService<User> {

    Result<?> login(LoginDTO loginDTO, HttpServletResponse response);

    Result<?> logout(HttpServletResponse response);

    Result<?> register(RegisterDTO registerDTO);

    List<UserListItemVO> listAllUsers(boolean isSuperAdmin);
}
