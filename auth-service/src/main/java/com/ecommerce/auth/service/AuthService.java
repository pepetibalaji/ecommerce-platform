package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.*;
import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final long REFRESH_TOKEN_TTL_DAYS = 7;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User account is not active");
        }

        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.getExpiry().isBefore(LocalDateTime.now())) {
            refreshTokenService.revoke(request.getRefreshToken());
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = refreshToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User account is not active");
        }

        refreshTokenService.revoke(request.getRefreshToken());
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshTokenValue = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusDays(REFRESH_TOKEN_TTL_DAYS);

        refreshTokenService.issue(user, refreshTokenValue, expiry);

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                "Bearer",
                jwtTokenService.getAccessTokenTtlSeconds(),
                toUserResponse(user)
        );
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}