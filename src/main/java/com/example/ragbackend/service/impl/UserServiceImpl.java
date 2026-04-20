package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.constant.SystemSpaceConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.entity.User;
import com.example.ragbackend.exception.BusinessException;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import com.example.ragbackend.mapper.SpaceMemberMapper;
import com.example.ragbackend.mapper.UserMapper;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.service.UserService;
import com.example.ragbackend.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final KnowledgeSpaceMapper knowledgeSpaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;

    public UserServiceImpl(KnowledgeSpaceMapper knowledgeSpaceMapper, SpaceMemberMapper spaceMemberMapper) {
        this.knowledgeSpaceMapper = knowledgeSpaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
    }

    @Override
    public Result<?> login(LoginDTO loginDTO, HttpServletResponse response) {
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername()));

        if (user == null || !encoder.matches(loginDTO.getPassword(), user.getPassword())) {
            return Result.error("Invalid username or password");
        }

        String token = JwtUtils.createToken(user.getUsername(), user.getId(), user.getRole());

        Cookie cookie = new Cookie("jwt_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
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
        Cookie cookie = new Cookie("jwt_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return Result.success("Logout successful");
    }

    @Override
    @Transactional
    public Result<?> register(RegisterDTO registerDTO) {
        String username = registerDTO.getUsername();
        String password = registerDTO.getPassword();
        String confirmPassword = registerDTO.getConfirmPassword();
        String email = registerDTO.getEmail();

        if (username == null || password == null || email == null) {
            return Result.error("All fields are required");
        }
        if (!password.equals(confirmPassword)) {
            return Result.error("The two passwords do not match");
        }

        String pwdRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,16}$";
        if (!password.matches(pwdRegex)) {
            return Result.error("Password must be 8-16 characters and contain both letters and digits");
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!email.matches(emailRegex)) {
            return Result.error("Invalid email format");
        }

        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .or()
                .eq(User::getEmail, email));
        if (count > 0) {
            return Result.error("Username or email is already in use");
        }

        KnowledgeSpace publicSpace = getRequiredPublicSpace();

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setRole("USER");
        user.setCreateTime(LocalDateTime.now());

        boolean saved = this.save(user);
        if (!saved) {
            return Result.error("Registration failed");
        }

        SpaceMember membership = new SpaceMember();
        membership.setSpaceId(publicSpace.getId());
        membership.setUserId(user.getId());
        membership.setRole("MEMBER");
        membership.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(membership);

        return Result.success("Registration successful");
    }

    @Override
    public List<UserListItemVO> listAllUsers(boolean isSuperAdmin) {
        if (!isSuperAdmin) {
            throw new BusinessException(403, "Only super admin can view all users");
        }

        return this.list(new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime))
                .stream()
                .map(this::toUserListItem)
                .collect(Collectors.toList());
    }

    private KnowledgeSpace getRequiredPublicSpace() {
        KnowledgeSpace publicSpace = knowledgeSpaceMapper.selectOne(new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getCode, SystemSpaceConstants.PUBLIC_SPACE_CODE)
                .last("LIMIT 1"));
        if (publicSpace == null) {
            throw new IllegalStateException("Public knowledge space is not initialized");
        }
        return publicSpace;
    }

    private UserListItemVO toUserListItem(User user) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
