package com.example.ragbackend.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatar;
    private String phone;
    private String role;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
