package com.example.ragbackend.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof Long) {
            return (Long) authentication.getCredentials();
        }
        throw new RuntimeException("Current user information was not found in security context");
    }

    /**
     * 该方法判断当前用户是否为超级管理员
     *
     * 1. 从 Spring Security 上下文获取认证信息
     * 2. 若无认证信息则返回 false
     * 3. 检查用户权限列表中是否包含 `ROLE_SUPER_ADMIN` 或 `ROLE_ADMIN` 角色
     *
     * 用于控制需要管理员权限的操作访问。
     * @return
     */

    public static boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    public static boolean isAdmin() {
        return isSuperAdmin();
    }
}
