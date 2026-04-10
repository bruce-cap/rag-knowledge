package com.example.ragbackend.config;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

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
                    String role = JwtUtils.getRoleFromToken(token);

                    // 构建权限列表
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER"))
                    );

                    // 构建认证对象
                    // 注意：我们将 userId 存放在 credentials 位置，方便 SecurityUtils 获取
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, userId, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token 无效或解析失败，使用统一的 Result 结构返回，保证和其他接口格式一致
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                ObjectMapper objectMapper = new ObjectMapper();
                response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, "未授权或Token已失效")));
                return;
            }
        }

        // 继续执行后续逻辑
        filterChain.doFilter(request, response);
    }
}