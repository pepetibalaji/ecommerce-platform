package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final TokenBlacklistService tokenBlacklistService;

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new ResourceAlreadyExistsException(
                    "User already exists with email: " + email
            );
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0L)
                .build();

        user = userRepository.save(user);
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (refreshToken.getExpiry().isBefore(LocalDateTime.now())) {
            refreshTokenService.revoke(request.getRefreshToken());
            throw new UnauthorizedException("Refresh token expired");
        }

        User user = refreshToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        refreshTokenService.revoke(request.getRefreshToken());
        return issueTokens(user);
    }

    public void logout(Jwt jwt, String refreshTokenValue) {
        if (jwt == null) {
            throw new UnauthorizedException("Missing access token");
        }

        tokenBlacklistService.blacklistToken(jwt);

        String userIdValue = jwt.getClaimAsString("userId");
        if (userIdValue == null || userIdValue.isBlank()) {
            throw new UnauthorizedException("Missing userId claim");
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdValue);
        } catch (Exception ex) {
            throw new UnauthorizedException("Invalid userId claim");
        }

        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenService.findByToken(refreshTokenValue).ifPresentOrElse(
                    token -> {
                        if (!token.getUser().getId().equals(userId)) {
                            throw new UnauthorizedException("Refresh token does not belong to current user");
                        }
                        refreshTokenService.revoke(refreshTokenValue);
                    },
                    () -> refreshTokenService.revokeAllForUser(userId)
            );
        } else {
            refreshTokenService.revokeAllForUser(userId);
        }
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }
}