package com.example.ragbackend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 注入加密器，用于密码比对
    @Bean
    public BCryptPasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    // 配置 CORS
    /*
    * 该代码配置了 CORS 跨域策略：
允许来源：仅允许 http://localhost:5173 前端访问
允许方法：支持 GET、POST、PUT、DELETE、OPTIONS 请求
允许所有请求头：设置 * 通配符
允许携带凭证：支持 Cookie 等认证信息
预检缓存：浏览器缓存跨域配置 1 小时*/
//    @Bean
//    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
//        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
//        configuration.setAllowedOrigins(java.util.List.of("http://localhost:5173"));
//        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
//        configuration.setAllowedHeaders(java.util.List.of("*"));
//        configuration.setAllowCredentials(true);
//        configuration.setMaxAge(3600L);
//        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }


    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    /*
    * 这段代码配置了 Spring Security 的安全过滤链：
禁用 CSRF 防护：适用于无状态的 JWT 认证
设置无会话策略：不创建和使用 Session，实现 Stateless 认证
配置访问规则：/auth/** 路径公开访问，其他所有请求需要认证
添加 JWT 过滤器：在用户名密码认证过滤器之前插入自定义的 JWT 验证过滤器，拦截并验证 Token
    * */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
//                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 关键：关闭 Session
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                // 重点：在 UsernamePasswordAuthenticationFilter 之前添加我们的 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
//    @Bean
//    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable) // 禁用跨站请求伪造保护（前后端分离不需要）
//                .authorizeHttpRequests(auth -> auth
////                        .requestMatchers("/auth/**", "/api/**").permitAll() // 允许所有人访问登录接口
//                        .requestMatchers("/auth/**").permitAll() // 允许所有人访问登录接口
//                        .anyRequest().authenticated()           // 其他接口必须登录
//
//
//        return http.build();
//}
}