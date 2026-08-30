package com.ecommerce.auth.security;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.service.TokenBlacklistService;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtBlacklistValidator implements OAuth2TokenValidator<Jwt> {

  private final TokenBlacklistService tokenBlacklistService;
  private final UserRepository userRepository;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (jwt == null) {
      return failure("JWT is missing");
    }

    if (jwt.getId() != null && tokenBlacklistService.isBlacklisted(jwt.getId())) {
      return failure("Token has been revoked");
    }

    String userIdValue = jwt.getClaimAsString("userId");
    if (userIdValue == null || userIdValue.isBlank()) {
      return failure("Missing userId claim");
    }

    UUID userId;
    try {
      userId = UUID.fromString(userIdValue);
    } catch (Exception ex) {
      return failure("Invalid userId claim");
    }

    User user = userRepository.findById(userId).orElse(null);
    if (user == null) {
      return failure("User not found");
    }

    if (user.getStatus() != UserStatus.ACTIVE) {
      return failure("User is not active");
    }

    Long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
    Object tokenVersionValue = jwt.getClaims().get("tokenVersion");
    Long tokenVersion = null;

    if (tokenVersionValue instanceof Number number) {
      tokenVersion = number.longValue();
    } else if (tokenVersionValue instanceof String value && !value.isBlank()) {
      try {
        tokenVersion = Long.parseLong(value);
      } catch (NumberFormatException ex) {
        return failure("Invalid tokenVersion claim");
      }
    }

    if (!Objects.equals(currentVersion, tokenVersion)) {
      return failure("Token version mismatch");
    }

    return OAuth2TokenValidatorResult.success();
  }

  private OAuth2TokenValidatorResult failure(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
  }
}
