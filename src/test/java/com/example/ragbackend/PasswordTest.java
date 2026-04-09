package com.example.ragbackend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // 生成 123456 对应的密文
        String result = encoder.encode("123456");
        System.out.println("请将下面这段密文复制到数据库的 password 字段中：");
        System.out.println(result);
    }
}