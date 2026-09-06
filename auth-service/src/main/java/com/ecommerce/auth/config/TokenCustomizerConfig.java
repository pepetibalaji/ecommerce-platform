package com.ecommerce.auth.config;

import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.auth.service.AuditRequestContext;
import com.ecommerce.auth.service.AuthAuditService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
@RequiredArgsConstructor
public class TokenCustomizerConfig {

  private final UserRepository userRepository;
  private final AuthorizationServerProperties authorizationServerProperties;
  private final AuthAuditService audit;

  @Bean
  public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
    return context -> {
      if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        return;
      }

      String email = context.getPrincipal().getName();
      String clientId = context.getRegisteredClient().getClientId();
      User user =
          userRepository
              .findAuthorizationDataByEmailNormalized(email.trim().toLowerCase(Locale.ROOT))
              .orElse(null);

      if (user == null) {
        audit.recordAttempt(
            null,
            null,
            "OAUTH_ACCESS_TOKEN_ISSUED",
            AuthAuditOutcome.FAILURE,
            AuditRequestContext.empty(),
            Map.of("reason", "user_not_found", "clientId", clientId));
        throw new OAuth2AuthenticationException(
            new OAuth2Error("invalid_grant", "User not found", null));
      }

      if (!user.isActiveAndVerified()) {
        audit.recordAttempt(
            user.getId(),
            user.getId(),
            "OAUTH_ACCESS_TOKEN_ISSUED",
            AuthAuditOutcome.DENIED,
            AuditRequestContext.empty(),
            Map.of("reason", "account_not_active_and_verified", "clientId", clientId));
        throw new OAuth2AuthenticationException(
            new OAuth2Error("invalid_grant", "User account is not active and verified", null));
      }

      context
          .getClaims()
          .subject(user.getEmail())
          .claim("userId", user.getId().toString())
          .claim("role", user.getPrimaryRoleCode())
          .claim("roles", user.getRoleCodes())
          .claim("permissions", user.getPermissionCodes())
          .claim("email_verified", true)
          .claim("status", user.getStatus().name())
          .claim("tokenVersion", user.getTokenVersion() == null ? 0L : user.getTokenVersion());

      if (!authorizationServerProperties.getAudiences().isEmpty()) {
        context.getClaims().audience(List.copyOf(authorizationServerProperties.getAudiences()));
      }

      audit.record(
          user.getId(),
          user.getId(),
          "OAUTH_ACCESS_TOKEN_ISSUED",
          AuthAuditOutcome.SUCCESS,
          AuditRequestContext.empty(),
          Map.of("clientId", clientId));
    };
  }
}
