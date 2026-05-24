package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshTokenRequest;
import com.ecommerce.auth.dto.RegisterRequest;

import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.User;

import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;

import com.ecommerce.auth.repository.RefreshTokenRepository;
import com.ecommerce.auth.repository.UserRepository;

import com.ecommerce.auth.security.JwtService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;

import com.ecommerce.common.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor

public class AuthService {

    private final UserRepository userRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final AuthenticationManager authenticationManager;

    @Transactional
    public String register(RegisterRequest request) {

            if (userRepository.existsByEmail(request.getEmail())) {
                    throw new ResourceAlreadyExistsException("Email already exists");
            }
            User user = User.builder()
                            .name(request.getName())
                            .email(request.getEmail())
                            .password(passwordEncoder.encode(request.getPassword()))
                            .role(Role.CUSTOMER)
                            .status(UserStatus.ACTIVE)
                            .build();
            userRepository.save(user);

            return "User registered successfully";
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

            authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                            request.getEmail(),
                                            request.getPassword()));

            User user = userRepository.findByEmail(request.getEmail())
                            .orElseThrow(() -> new ResourceAlreadyExistsException("Email already exists"));

            String accessToken = jwtService.generateAccessToken(user);

            String refreshToken = jwtService.generateRefreshToken(user);

            refreshTokenRepository.deleteByUser(user);

            RefreshToken refreshTokenEntity = RefreshToken.builder()
                            .user(user)
                            .token(refreshToken)
                            .expiry(LocalDateTime.now().plusDays(7))
                            .build();

            refreshTokenRepository.save(refreshTokenEntity);

            return AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .tokenType("Bearer")
                            .build();
    }

    @Transactional
    public AuthResponse refreshToken(
                RefreshTokenRequest request
        ) {

        RefreshToken refreshToken =
                refreshTokenRepository
                        .findByToken(request.getRefreshToken())
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Invalid refresh token")
                        );

        if (refreshToken.getExpiry().isBefore(
                LocalDateTime.now()
        )) {

                throw new ResourceNotFoundException("Refresh token expired");
        }

        User user = refreshToken.getUser();

        String accessToken =
                jwtService.generateAccessToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .build();
        }
}