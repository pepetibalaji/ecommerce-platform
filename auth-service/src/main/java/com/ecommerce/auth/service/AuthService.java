package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.UserResponse;
import com.ecommerce.auth.entity.RefreshSession;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.repository.RefreshSessionRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and rotates first-party tokens while maintaining a durable security audit trail. */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository users;
  private final RefreshSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final JwtTokenService jwtTokens;
  private final TokenBlacklistService blacklist;
  private final AuthAuditService audit;
  private final AuthAbuseProtection abuseProtection;

  @Transactional
  public AuthResponse login(LoginRequest request) {
    return login(request, AuditRequestContext.empty());
  }

  @Transactional
  public AuthResponse login(LoginRequest request, AuditRequestContext context) {
    abuseProtection.check("login", request.getEmail(), context);
    User user = users.findByEmailNormalized(normalize(request.getEmail())).orElse(null);
    if (user == null) {
      audit.recordAttempt(
          null,
          null,
          "LOGIN",
          AuthAuditOutcome.FAILURE,
          context,
          Map.of("reason", "invalid_credentials"));
      throw invalidCredentials();
    }

    if (!user.isActiveAndVerified() || !passwords.matches(request.getPassword(), user.getPasswordHash())) {
      audit.recordAttempt(
          user.getId(),
          user.getId(),
          "LOGIN",
          AuthAuditOutcome.FAILURE,
          context,
          Map.of("reason", "invalid_credentials"));
      throw invalidCredentials();
    }

    AuthResponse response = issue(user, UUID.randomUUID(), context);
    audit.record(user.getId(), user.getId(), "LOGIN", AuthAuditOutcome.SUCCESS, context, Map.of());
    return response;
  }

  @Transactional(noRollbackFor = UnauthorizedException.class)
  public AuthResponse refresh(RefreshRequest request) {
    return refresh(request, AuditRequestContext.empty());
  }

  @Transactional(noRollbackFor = UnauthorizedException.class)
  public AuthResponse refresh(RefreshRequest request, AuditRequestContext context) {
    abuseProtection.check("refresh", null, context);
    RefreshSession old = sessions.findByTokenHash(hash(request.getRefreshToken())).orElse(null);
    if (old == null) {
      audit.recordAttempt(
          null,
          null,
          "TOKEN_REFRESH",
          AuthAuditOutcome.FAILURE,
          context,
          Map.of("reason", "invalid_refresh_token"));
      throw invalidRefreshToken();
    }

    Instant now = Instant.now();
    UUID userId = old.getUser().getId();
    if (old.getRevokedAt() != null) {
      revokeFamily(old.getTokenFamilyId(), now);
      User user = old.getUser();
      user.setTokenVersion(user.getTokenVersion() + 1);
      users.save(user);
      publishTokenVersion(user);
      audit.recordAttempt(
          userId,
          userId,
          "REFRESH_TOKEN_REUSE_DETECTED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of());
      throw invalidRefreshToken();
    }

    if (old.getExpiresAt().isBefore(now) || !old.getUser().isActiveAndVerified()) {
      old.setRevokedAt(now);
      audit.recordAttempt(
          userId,
          userId,
          "TOKEN_REFRESH",
          AuthAuditOutcome.FAILURE,
          context,
          Map.of("reason", "expired_or_inactive"));
      throw invalidRefreshToken();
    }

    AuthResponse response = issue(old.getUser(), old.getTokenFamilyId(), context);
    RefreshSession replacement =
        sessions.findByTokenHash(hash(response.getRefreshToken())).orElseThrow();
    old.setRevokedAt(now);
    old.setLastUsedAt(now);
    old.setReplacedBySessionId(replacement.getId());
    audit.record(userId, userId, "TOKEN_REFRESH", AuthAuditOutcome.SUCCESS, context, Map.of());
    return response;
  }

  @Transactional
  public void logout(Jwt jwt, String refreshToken) {
    logout(jwt, refreshToken, AuditRequestContext.empty());
  }

  @Transactional
  public void logout(Jwt jwt, String refreshToken, AuditRequestContext context) {
    UUID actorUserId = userId(jwt);
    if (jwt != null) {
      blacklist.blacklistToken(jwt);
    }

    if (refreshToken != null && !refreshToken.isBlank()) {
      sessions
          .findByTokenHash(hash(refreshToken))
          .ifPresent(
              session -> {
                UUID sessionUserId = session.getUser().getId();
                if (actorUserId != null && !actorUserId.equals(sessionUserId)) {
                  audit.recordAttempt(
                      actorUserId,
                      sessionUserId,
                      "LOGOUT",
                      AuthAuditOutcome.DENIED,
                      context,
                      Map.of("reason", "refresh_session_owner_mismatch"));
                  throw invalidRefreshToken();
                }
                session.setRevokedAt(Instant.now());
              });
    }

    audit.record(actorUserId, actorUserId, "LOGOUT", AuthAuditOutcome.SUCCESS, context, Map.of());
  }

  /** Revokes every active refresh session for a user. The caller records the business-level audit event. */
  @Transactional
  public void revokeAll(UUID userId) {
    sessions.findByUser_IdAndRevokedAtIsNull(userId).forEach(session -> session.setRevokedAt(Instant.now()));
  }

  public void publishTokenVersion(User user) {
    blacklist.publishTokenVersion(user.getId(), user.getTokenVersion() == null ? 0L : user.getTokenVersion());
  }

  private AuthResponse issue(User user, UUID family, AuditRequestContext context) {
    String raw = token();
    AuditRequestContext safeContext = context == null ? AuditRequestContext.empty() : context;
    sessions.save(
        RefreshSession.builder()
            .user(user)
            .tokenHash(hash(raw))
            .tokenFamilyId(family)
            .expiresAt(Instant.now().plus(Duration.ofDays(7)))
            .ipAddress(safeContext.ipAddress())
            .userAgent(safeContext.userAgent())
            .build());
    return new AuthResponse(
        jwtTokens.generateAccessToken(user),
        raw,
        "Bearer",
        jwtTokens.getAccessTokenTtlSeconds(),
        profile(user));
  }

  private void revokeFamily(UUID family, Instant now) {
    sessions.findByTokenFamilyIdAndRevokedAtIsNull(family).forEach(session -> session.setRevokedAt(now));
  }

  private UserResponse profile(User user) {
    return new UserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRoleCodes(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private UUID userId(Jwt jwt) {
    if (jwt == null) {
      return null;
    }
    try {
      return UUID.fromString(jwt.getClaimAsString("userId"));
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return null;
    }
  }

  private UnauthorizedException invalidCredentials() {
    return new UnauthorizedException("Invalid credentials");
  }

  private UnauthorizedException invalidRefreshToken() {
    return new UnauthorizedException("Invalid refresh token");
  }

  private String token() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
