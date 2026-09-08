package com.ecommerce.auth.service;

import com.ecommerce.auth.config.AuthorizationServerProperties;
import com.ecommerce.auth.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

  private final JwtEncoder jwtEncoder;
  private final AuthorizationServerSettings authorizationServerSettings;
  private final AuthorizationServerProperties authorizationServerProperties;
  private final TokenBlacklistService tokenBlacklistService;

  @Value("${auth.token.access-ttl-minutes:30}")
  private long accessTtlMinutes;

  public String generateAccessToken(User user) {
    tokenBlacklistService.publishTokenVersion(user.getId(), user.getTokenVersion() == null ? 0L : user.getTokenVersion());
    Instant now = Instant.now();
    Instant expiry = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
    String jti = UUID.randomUUID().toString();

    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(authorizationServerSettings.getIssuer())
            .issuedAt(now)
            .expiresAt(expiry)
            .subject(user.getEmail())
            .id(jti)
            .claim("userId", user.getId().toString())
            .claim("role", user.getPrimaryRoleCode())
            .claim("roles", user.getRoleCodes())
            .claim("permissions", user.getPermissionCodes())
            .claim("email_verified", user.getEmailVerifiedAt() != null)
            .claim("status", user.getStatus().name())
            .claim("tokenVersion", user.getTokenVersion());

    if (!authorizationServerProperties.getAudiences().isEmpty()) {
      claims.audience(List.copyOf(authorizationServerProperties.getAudiences()));
    }

    return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
  }

  public long getAccessTokenTtlSeconds() {
    return ChronoUnit.MINUTES.getDuration().multipliedBy(accessTtlMinutes).toSeconds();
  }
}
