package com.ecommerce.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:blacklist:jti:";

    private final RedisTemplate<String, String> redisTemplate;

    public void blacklistToken(Jwt jwt) {
        if (jwt == null) {
            return;
        }
        blacklist(jwt.getId(), jwt.getExpiresAt());
    }

    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().set(key(jti), "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(jti)));
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}