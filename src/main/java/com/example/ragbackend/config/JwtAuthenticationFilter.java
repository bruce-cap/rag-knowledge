package com.example.ragbackend.config;

import com.example.ragbackend.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 从请求头获取 Authorization
        String authHeader = request.getHeader("Authorization");

        // 2. 校验 Header 格式（必须以 Bearer 开头）
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // 截取掉 "Bearer " 后的部分

            try {
                // 3. 解析并校验 Token（利用你之前的 JwtUtils）
                // 这里的 validateToken 需要在 JwtUtils 里补充一个方法（见下方步骤 3）
                if (JwtUtils.validateToken(token)) {
                    String username = JwtUtils.getUsernameFromToken(token);

                    // 4. 构建认证对象并存入 Security 上下文
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token 无效或过期时返回401
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未授权或Token无效\"}");
                return;
            }
        }

        // 继续执行后续的过滤器逻辑
        filterChain.doFilter(request, response);
    }
}