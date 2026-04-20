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

    public static boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Historical alias kept for compatibility.
     * In the current role model, system-level admin means SUPER_ADMIN only.
     */
    public static boolean isAdmin() {
        return isSuperAdmin();
    }
}
