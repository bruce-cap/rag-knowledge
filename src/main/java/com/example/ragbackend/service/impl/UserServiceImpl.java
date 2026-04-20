package com.example.ragbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.ragbackend.common.Result;
import com.example.ragbackend.constant.SpaceJoinRequestConstants;
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
import com.example.ragbackend.model.dto.UserPasswordUpdateDTO;
import com.example.ragbackend.model.dto.UserProfileUpdateDTO;
import com.example.ragbackend.model.vo.UserDetailVO;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.model.vo.UserProfileVO;
import com.example.ragbackend.service.UserService;
import com.example.ragbackend.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final KnowledgeSpaceMapper knowledgeSpaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;

    public UserServiceImpl(KnowledgeSpaceMapper knowledgeSpaceMapper, SpaceMemberMapper spaceMemberMapper) {
        this.knowledgeSpaceMapper = knowledgeSpaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
    }

    @Override
    public Result<?> login(LoginDTO loginDTO, HttpServletResponse response) {
        log.info("Attempt login, username={}", loginDTO.getUsername());
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, loginDTO.getUsername())
                .last("LIMIT 1"));

        if (user == null || !encoder.matches(loginDTO.getPassword(), user.getPassword())) {
            log.warn("Login failed due to invalid credentials, username={}", loginDTO.getUsername());
            return Result.error("Invalid username or password");
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            log.warn("Login blocked because account disabled, userId={}, username={}", user.getId(), user.getUsername());
            return Result.error(403, "The account has been disabled");
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
        map.put("status", user.getStatus());
        map.put("nickname", user.getNickname());

        log.info("Login succeeded, userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
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
        log.info("Logout succeeded");
        return Result.success("Logout successful");
    }

    @Override
    @Transactional
    public Result<?> register(RegisterDTO registerDTO) {
        String username = registerDTO.getUsername();
        String password = registerDTO.getPassword();
        String confirmPassword = registerDTO.getConfirmPassword();
        String email = registerDTO.getEmail();

        log.info("Register new user, username={}, email={}", username, email);
        if (username == null || password == null || confirmPassword == null || email == null) {
            return Result.error("All fields are required");
        }
        if (!password.equals(confirmPassword)) {
            return Result.error("The two passwords do not match");
        }
        validatePassword(password);
        validateEmail(email);

        long count = count(new LambdaQueryWrapper<User>()
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
        user.setStatus(STATUS_ENABLED);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setNickname(username);

        if (!save(user)) {
            log.warn("Register failed when saving user, username={}", username);
            return Result.error("Registration failed");
        }

        SpaceMember membership = new SpaceMember();
        membership.setSpaceId(publicSpace.getId());
        membership.setUserId(user.getId());
        membership.setRole(SpaceJoinRequestConstants.VIEW_ROLE);
        membership.setJoinTime(LocalDateTime.now());
        spaceMemberMapper.insert(membership);

        log.info("Register succeeded, userId={}, username={}, publicSpaceId={}", user.getId(), user.getUsername(), publicSpace.getId());
        return Result.success("Registration successful");
    }

    @Override
    public List<UserListItemVO> listAllUsers(boolean isSuperAdmin) {
        ensureSuperAdmin(isSuperAdmin);
        return list(new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime))
                .stream()
                .map(this::toUserListItem)
                .collect(Collectors.toList());
    }

    @Override
    public UserDetailVO getUserDetail(Long targetUserId, boolean isSuperAdmin) {
        ensureSuperAdmin(isSuperAdmin);
        return toUserDetail(getRequiredUser(targetUserId));
    }

    @Override
    public UserDetailVO updateUserStatus(Long targetUserId, Integer status, boolean isSuperAdmin) {
        ensureSuperAdmin(isSuperAdmin);
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException(400, "Status must be 0 or 1");
        }

        User user = getRequiredUser(targetUserId);
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
        log.info("User status updated, targetUserId={}, status={}", targetUserId, status);
        return toUserDetail(user);
    }

    @Override
    public UserProfileVO getCurrentUserProfile(Long userId) {
        return toUserProfile(getRequiredUser(userId));
    }

    @Override
    public UserProfileVO updateCurrentUserProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = getRequiredUser(userId);
        if (dto == null) {
            throw new BusinessException(400, "Profile payload cannot be empty");
        }

        if (dto.getEmail() != null) {
            validateEmail(dto.getEmail());
            ensureEmailNotUsedByOthers(dto.getEmail(), userId);
            user.setEmail(dto.getEmail().trim());
        }
        if (dto.getNickname() != null) {
            user.setNickname(trimToNull(dto.getNickname()));
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(trimToNull(dto.getAvatar()));
        }
        if (dto.getPhone() != null) {
            user.setPhone(trimToNull(dto.getPhone()));
        }

        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
        log.info("User profile updated, userId={}", userId);
        return toUserProfile(user);
    }

    @Override
    public void updateCurrentUserPassword(Long userId, UserPasswordUpdateDTO dto) {
        if (dto == null || dto.getOldPassword() == null || dto.getNewPassword() == null || dto.getConfirmPassword() == null) {
            throw new BusinessException(400, "Password payload is incomplete");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "The two passwords do not match");
        }
        validatePassword(dto.getNewPassword());

        User user = getRequiredUser(userId);
        if (!encoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "The current password is incorrect");
        }

        user.setPassword(encoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
        log.info("User password updated, userId={}", userId);
    }

    private void ensureEmailNotUsedByOthers(String email, Long currentUserId) {
        long count = count(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email.trim())
                .ne(User::getId, currentUserId));
        if (count > 0) {
            throw new BusinessException(400, "Email is already in use");
        }
    }

    private void validatePassword(String password) {
        String pwdRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,16}$";
        if (!password.matches(pwdRegex)) {
            throw new BusinessException(400, "Password must be 8-16 characters and contain both letters and digits");
        }
    }

    private void validateEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (email == null || !email.matches(emailRegex)) {
            throw new BusinessException(400, "Invalid email format");
        }
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

    private User getRequiredUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "User not found");
        }
        return user;
    }

    private void ensureSuperAdmin(boolean isSuperAdmin) {
        if (!isSuperAdmin) {
            throw new BusinessException(403, "Only super admin can perform this operation");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserListItemVO toUserListItem(User user) {
        UserListItemVO vo = new UserListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private UserDetailVO toUserDetail(User user) {
        UserDetailVO vo = new UserDetailVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private UserProfileVO toUserProfile(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
