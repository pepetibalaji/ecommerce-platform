package com.ecommerce.common.security.jwt;

import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Enforces Auth's centrally published session version at every resource service. */
public class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {
  private final StringRedisTemplate redis;
  public TokenVersionValidator(StringRedisTemplate redis) { this.redis = redis; }
  @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
    String userId = jwt.getClaimAsString("userId");
    Object tokenVersion = jwt.getClaim("tokenVersion");
    if (userId == null || tokenVersion == null) return failure();
    String current = redis.opsForValue().get("auth:token-version:" + userId);
    return current != null && Objects.equals(current, String.valueOf(tokenVersion))
        ? OAuth2TokenValidatorResult.success() : failure();
  }
  private OAuth2TokenValidatorResult failure() {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token has been invalidated", null));
  }
}
