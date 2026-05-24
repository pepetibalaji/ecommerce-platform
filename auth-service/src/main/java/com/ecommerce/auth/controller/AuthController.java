package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshTokenRequest;
import com.ecommerce.auth.dto.RegisterRequest;

import com.ecommerce.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")

@RequiredArgsConstructor

public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(
           @Valid @RequestBody RegisterRequest request
    ) {

        return ResponseEntity.ok(
                authService.register(request)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
           @Valid @RequestBody LoginRequest request
    ) {

            return ResponseEntity.ok(
                            authService.login(request));
    }
    @PostMapping("/refresh")
     public ResponseEntity<AuthResponse> refreshToken(
        @Valid @RequestBody RefreshTokenRequest request) {
     return ResponseEntity.ok(
            authService.refreshToken(request)
     );
}
}