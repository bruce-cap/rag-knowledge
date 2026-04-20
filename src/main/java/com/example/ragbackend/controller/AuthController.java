package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.model.dto.LoginDTO;
import com.example.ragbackend.model.dto.RegisterDTO;
import com.example.ragbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
@Slf4j
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginDTO loginDTO) {
        log.info("Login request received, username={}", loginDTO.getUsername());
        return userService.login(loginDTO);
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterDTO registerDTO) {
        log.info("Register request received, username={}, email={}", registerDTO.getUsername(), registerDTO.getEmail());
        return userService.register(registerDTO);
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        log.info("Logout request received");
        return userService.logout();
    }
}
