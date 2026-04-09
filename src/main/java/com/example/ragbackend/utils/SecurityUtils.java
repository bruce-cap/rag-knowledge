package com.example.ragbackend.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof Long) {
            // 我们在 Filter 里把 userId 存到了 Credentials 位置（也可以存 Principal）
            return (Long) authentication.getCredentials();
        }
        throw new RuntimeException("当前上下文未找到用户信息");
    }
}