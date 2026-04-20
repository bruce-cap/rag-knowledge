package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class UserProfileUpdateDTO {

    private String nickname;

    private String email;

    private String avatar;

    private String phone;
}
