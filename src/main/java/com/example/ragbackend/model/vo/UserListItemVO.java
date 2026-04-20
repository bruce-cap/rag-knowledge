package com.example.ragbackend.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserListItemVO {

    private Long id;

    private String username;

    private String email;

    private String role;

    private LocalDateTime createTime;
}
