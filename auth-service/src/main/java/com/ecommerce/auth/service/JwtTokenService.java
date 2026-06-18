package com.ecommerce.auth.service;

import com.ecommerce.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthorizationServerSettings authorizationServerSettings;

    @Value("${auth.token.access-ttl-minutes:30}")
    private long accessTtlMinutes;

    public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
    String jti = UUID.randomUUID().toString();

    JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(authorizationServerSettings.getIssuer())
            .issuedAt(now)
            .expiresAt(expiry)
            .subject(user.getEmail())
            .id(jti)
            .claim("userId", user.getId().toString())
            .claim("role", user.getRole().name())
            .claim("status", user.getStatus().name())
            .claim("tokenVersion", user.getTokenVersion())
            .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return ChronoUnit.MINUTES.getDuration()
                .multipliedBy(accessTtlMinutes)
                .toSeconds();
    }
}