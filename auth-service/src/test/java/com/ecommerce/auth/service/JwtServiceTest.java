package com.ecommerce.auth.service;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private User user;

    @BeforeEach
    void setUp() {

        jwtService = new JwtService();

        ReflectionTestUtils.setField(
                jwtService,
                "secret",
                "7e1f3f8a6c4d9b2e5a7f1c3d8e6b9f2a7c4d1e8f3a6b5c2d9e7f1a3c5b8d2e6"
        );

        ReflectionTestUtils.setField(
                jwtService,
                "accessTokenExpiration",
                900000L
        );

        ReflectionTestUtils.setField(
                jwtService,
                "refreshTokenExpiration",
                604800000L
        );

        user = User.builder()
                .email("test@example.com")
                .role(Role.CUSTOMER)
                .build();
    }

    @Test
    void shouldGenerateAccessToken() {

        String token =
                jwtService.generateAccessToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldGenerateRefreshToken() {

        String token =
                jwtService.generateRefreshToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldExtractEmailFromToken() {

        String token =
                jwtService.generateAccessToken(user);

        String email =
                jwtService.extractEmail(token);

        assertEquals(
                "test@example.com",
                email
        );
    }

    @Test
    void shouldValidateToken() {

        String token =
                jwtService.generateAccessToken(user);

        assertTrue(
                jwtService.isTokenValid(token)
        );
    }

    @Test
    void shouldRejectInvalidToken() {

        assertFalse(
                jwtService.isTokenValid("invalid-token")
        );
    }
}