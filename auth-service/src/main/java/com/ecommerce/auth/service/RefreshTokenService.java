package com.ecommerce.auth.service;

import com.ecommerce.auth.entity.RefreshToken;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public RefreshToken issue(User user, String tokenValue, LocalDateTime expiry) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiry(expiry)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(refreshTokenRepository::delete);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.deleteAllByUser_Id(userId);
    }
}