package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.model.dto.UserPasswordUpdateDTO;
import com.example.ragbackend.model.dto.UserProfileUpdateDTO;
import com.example.ragbackend.model.vo.UserDetailVO;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.model.vo.UserProfileVO;

import java.util.List;

public interface UserService extends IService<User> {

    Result<?> login(LoginDTO loginDTO);

    Result<?> logout();

    Result<?> register(RegisterDTO registerDTO);

    List<UserListItemVO> listAllUsers(boolean isSuperAdmin);

    UserDetailVO getUserDetail(Long targetUserId, boolean isSuperAdmin);

    UserDetailVO updateUserStatus(Long targetUserId, Integer status, boolean isSuperAdmin);

    UserProfileVO getCurrentUserProfile(Long userId);

    UserProfileVO updateCurrentUserProfile(Long userId, UserProfileUpdateDTO dto);

    void updateCurrentUserPassword(Long userId, UserPasswordUpdateDTO dto);
}
