package com.example.ragbackend.config;

import com.example.ragbackend.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.info("正在拦截请求: {}", request.getRequestURI());
        String token = null;

        // 1. 优先从 Header 获取 (Authorization: Bearer <token>)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. 如果 Header 中没有，则尝试从 Cookie 中获取
        if (token == null && request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("jwt_token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        // 3. 如果找到了 token，进行校验和上下文设置
        if (token != null) {
            try {
                if (JwtUtils.validateToken(token)) {
                    String username = JwtUtils.getUsernameFromToken(token);
                    Long userId = JwtUtils.getUserIdFromToken(token);

                    // 构建认证对象
                    // 注意：我们将 userId 存放在 credentials 位置，方便 SecurityUtils 获取
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, userId, Collections.emptyList());

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token 无效或解析失败
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未授权或Token无效\"}");
                return;
            }
        }

        // 继续执行后续逻辑
        filterChain.doFilter(request, response);
    }
}