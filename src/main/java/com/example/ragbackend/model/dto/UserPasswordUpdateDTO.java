package com.example.ragbackend.model.dto;

import lombok.Data;

@Data
public class UserPasswordUpdateDTO {

    private String oldPassword;

    private String newPassword;

    private String confirmPassword;
}
