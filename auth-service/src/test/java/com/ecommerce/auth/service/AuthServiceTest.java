package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.RefreshTokenRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.security.JwtService;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldRegisterUserSuccessfully() {

        RegisterRequest request = new RegisterRequest(
                "Balaji",
                "balaji@test.com",
                "password123"
        );

        when(userRepository.existsByEmail(
                request.getEmail()
        )).thenReturn(false);

        when(passwordEncoder.encode(
                request.getPassword()
        )).thenReturn("encoded-password");

        String result = authService.register(request);

        assertEquals(
                "User registered successfully",
                result
        );

        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest(
                "Balaji",
                "balaji@test.com",
                "password123"
        );

        when(userRepository.existsByEmail(
                request.getEmail()
        )).thenReturn(true);

        assertThrows(
                ResourceAlreadyExistsException.class,
                () -> authService.register(request)
        );
    }

    @Test
    void shouldRefreshTokenSuccessfully() {

        User user = User.builder()
                .email("test@test.com")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-token")
                .user(user)
                .expiry(LocalDateTime.now().plusDays(1))
                .build();

        when(refreshTokenRepository.findByToken(
                "refresh-token"
        )).thenReturn(Optional.of(refreshToken));

        when(jwtService.generateAccessToken(user))
                .thenReturn("new-access-token");

        RefreshRequest request =
                new RefreshRequest("refresh-token");

        var response =
                authService.refreshToken(request);

        assertEquals(
                "new-access-token",
                response.getAccessToken()
        );

        assertEquals(
                "refresh-token",
                response.getRefreshToken()
        );
    }
}

