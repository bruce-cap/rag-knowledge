package com.example.ragbackend.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtils {
    // 密钥从环境变量读取，默认值仅用于开发
    private static final String SECRET_STR = System.getenv("JWT_SECRET") != null
            ? System.getenv("JWT_SECRET")
            : "dev_only_insecure_key_do_not_use_in_production";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET_STR.getBytes());
    private static final long EXPIRATION = 86400000; // 24小时

    public static String createToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(KEY)
                .compact();
    }

    // 在 JwtUtils 类中添加以下静态方法
    public static boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(KEY).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}