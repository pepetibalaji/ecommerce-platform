package com.ecommerce.auth.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Makes Spring Authorization Server's JDBC persistence safe for opaque refresh tokens.
 *
 * <p>The stock JDBC service writes the refresh token value verbatim and searches by that value.
 * This adapter persists its SHA-256 verifier instead and hashes the value only at refresh lookup.
 * The token endpoint returns the original token before this adapter is invoked, so clients retain
 * the normal OAuth contract while PostgreSQL never receives the raw refresh secret.
 */
public final class HashingOAuth2AuthorizationService implements OAuth2AuthorizationService {
  private final OAuth2AuthorizationService delegate;

  public HashingOAuth2AuthorizationService(OAuth2AuthorizationService delegate) {
    this.delegate = delegate;
  }

  @Override
  public void save(OAuth2Authorization authorization) {
    delegate.save(withHashedRefreshToken(authorization));
  }

  @Override
  public void remove(OAuth2Authorization authorization) {
    delegate.remove(authorization);
  }

  @Override
  public OAuth2Authorization findById(String id) {
    return delegate.findById(id);
  }

  @Override
  public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
    if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
      return delegate.findByToken(hash(token), tokenType);
    }
    return delegate.findByToken(token, tokenType);
  }

  private OAuth2Authorization withHashedRefreshToken(OAuth2Authorization authorization) {
    OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
    if (refresh == null) {
      return authorization;
    }
    OAuth2RefreshToken raw = refresh.getToken();
    OAuth2RefreshToken hashed =
        new OAuth2RefreshToken(hash(raw.getTokenValue()), raw.getIssuedAt(), raw.getExpiresAt());
    return OAuth2Authorization.from(authorization)
        .token(hashed, metadata -> metadata.putAll(refresh.getMetadata()))
        .build();
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
