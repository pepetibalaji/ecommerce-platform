package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.entity.RefreshSession;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.RefreshSessionRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository users;
  @Mock private RefreshSessionRepository sessions;
  @Mock private PasswordEncoder passwords;
  @Mock private JwtTokenService jwtTokens;
  @Mock private TokenBlacklistService blacklist;
  @Mock private AuthAuditService audit;
  @InjectMocks private AuthService service;

  private User user;

  @BeforeEach
  void setUp() {
    Role customer = new Role();
    customer.setCode("CUSTOMER");
    user = User.builder()
        .id(UUID.fromString("10000000-0000-0000-0000-000000000001"))
        .name("Jane Doe")
        .email("jane@example.com")
        .emailNormalized("jane@example.com")
        .passwordHash("encoded-password")
        .status(UserStatus.ACTIVE)
        .emailVerifiedAt(Instant.now().minusSeconds(60))
        .tokenVersion(0L)
        .roles(java.util.Set.of(customer))
        .build();
  }

  @Test
  void loginIssuesOpaqueRefreshTokenPersistsOnlyItsHashAndAuditsSuccess() throws Exception {
    stubTokenIssue();
    when(users.findByEmailNormalized("jane@example.com")).thenReturn(Optional.of(user));
    when(passwords.matches("Password@123", "encoded-password")).thenReturn(true);

    AuthResponse response = service.login(loginRequest(" JANE@example.com "));

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isNotBlank();
    ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
    verify(sessions).save(sessionCaptor.capture());
    RefreshSession session = sessionCaptor.getValue();
    assertThat(session.getTokenHash()).isNotEqualTo(response.getRefreshToken());
    assertThat(session.getTokenHash()).isEqualTo(hash(response.getRefreshToken()));
    assertThat(session.getTokenFamilyId()).isNotNull();
    assertThat(session.getExpiresAt()).isAfter(Instant.now());
    verify(audit)
        .record(
            eq(user.getId()),
            eq(user.getId()),
            eq("LOGIN"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            eq(Map.of()));
  }

  @Test
  void loginRejectsUnverifiedPendingUserAndAuditsFailure() {
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setEmailVerifiedAt(null);
    when(users.findByEmailNormalized("jane@example.com")).thenReturn(Optional.of(user));

    assertThrows(UnauthorizedException.class, () -> service.login(loginRequest("jane@example.com")));

    verify(audit)
        .recordAttempt(
            eq(user.getId()),
            eq(user.getId()),
            eq("LOGIN"),
            eq(AuthAuditOutcome.FAILURE),
            any(AuditRequestContext.class),
            eq(Map.of("reason", "invalid_credentials")));
  }

  @Test
  void refreshRotatesSessionKeepsFamilyAndAuditsSuccess() throws Exception {
    stubTokenIssue();
    String rawToken = "old-refresh-token";
    UUID family = UUID.randomUUID();
    RefreshSession old = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash(hash(rawToken)).tokenFamilyId(family).expiresAt(Instant.now().plusSeconds(600)).build();
    RefreshSession replacement = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash("replacement-hash").tokenFamilyId(family).expiresAt(Instant.now().plusSeconds(600)).build();
    AtomicInteger lookups = new AtomicInteger();
    when(sessions.findByTokenHash(anyString())).thenAnswer(invocation ->
        lookups.incrementAndGet() == 1 ? Optional.of(old) : Optional.of(replacement));

    AuthResponse response = service.refresh(refreshRequest(rawToken));

    assertThat(response.getRefreshToken()).isNotEqualTo(rawToken);
    assertThat(old.getRevokedAt()).isNotNull();
    assertThat(old.getLastUsedAt()).isNotNull();
    assertThat(old.getReplacedBySessionId()).isEqualTo(replacement.getId());
    ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
    verify(sessions).save(sessionCaptor.capture());
    assertThat(sessionCaptor.getValue().getTokenFamilyId()).isEqualTo(family);
    verify(audit)
        .record(
            eq(user.getId()),
            eq(user.getId()),
            eq("TOKEN_REFRESH"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            eq(Map.of()));
  }

  @Test
  void refreshTokenReuseRevokesEntireFamilyAndAuditsDetection() throws Exception {
    UUID family = UUID.randomUUID();
    RefreshSession reused = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash(hash("reused")).tokenFamilyId(family).expiresAt(Instant.now().plusSeconds(600))
        .revokedAt(Instant.now().minusSeconds(1)).build();
    RefreshSession familyPeer = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash("peer").tokenFamilyId(family).expiresAt(Instant.now().plusSeconds(600)).build();
    when(sessions.findByTokenHash(hash("reused"))).thenReturn(Optional.of(reused));
    when(sessions.findByTokenFamilyIdAndRevokedAtIsNull(family)).thenReturn(List.of(familyPeer));

    assertThrows(UnauthorizedException.class, () -> service.refresh(refreshRequest("reused")));

    assertThat(familyPeer.getRevokedAt()).isNotNull();
    verify(audit)
        .recordAttempt(
            eq(user.getId()),
            eq(user.getId()),
            eq("REFRESH_TOKEN_REUSE_DETECTED"),
            eq(AuthAuditOutcome.DENIED),
            any(AuditRequestContext.class),
            eq(Map.of()));
  }

  @Test
  void logoutRevokesOnlyItsRefreshSessionBlacklistsJwtAndAuditsSuccess() throws Exception {
    String rawToken = "current-refresh-token";
    RefreshSession session = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash(hash(rawToken)).tokenFamilyId(UUID.randomUUID()).expiresAt(Instant.now().plusSeconds(600)).build();
    when(sessions.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(session));
    Jwt jwt = jwt(user.getId());

    service.logout(jwt, rawToken);

    assertThat(session.getRevokedAt()).isNotNull();
    verify(blacklist).blacklistToken(jwt);
    verify(audit)
        .record(
            eq(user.getId()),
            eq(user.getId()),
            eq("LOGOUT"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            eq(Map.of()));
  }

  private LoginRequest loginRequest(String email) {
    LoginRequest request = new LoginRequest();
    request.setEmail(email);
    request.setPassword("Password@123");
    return request;
  }

  private RefreshRequest refreshRequest(String refreshToken) {
    RefreshRequest request = new RefreshRequest();
    request.setRefreshToken(refreshToken);
    return request;
  }

  private Jwt jwt(UUID userId) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .issuer("https://issuer.example")
        .subject(user.getEmail())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claim("userId", userId.toString())
        .build();
  }

  private String hash(String value) throws Exception {
    return java.util.HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private void stubTokenIssue() {
    when(jwtTokens.generateAccessToken(user)).thenReturn("access-token");
    when(jwtTokens.getAccessTokenTtlSeconds()).thenReturn(900L);
    when(sessions.save(any(RefreshSession.class))).thenAnswer(invocation -> {
      RefreshSession session = invocation.getArgument(0);
      if (session.getId() == null) {
        session.setId(UUID.randomUUID());
      }
      return session;
    });
  }
}
