package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.dto.ActionTokenRequest;
import com.ecommerce.auth.dto.DeliveryTokenResponse;
import com.ecommerce.auth.dto.EmailChangeRequest;
import com.ecommerce.auth.dto.ForgotPasswordRequest;
import com.ecommerce.auth.dto.ResetPasswordRequest;
import com.ecommerce.auth.dto.ResendVerificationRequest;
import com.ecommerce.auth.entity.IdentityActionToken;
import com.ecommerce.auth.entity.RefreshSession;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.entity.enums.IdentityActionType;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.IdentityActionTokenRepository;
import com.ecommerce.auth.repository.RefreshSessionRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ActionTokenServiceTest {

  private static final String RAW_TOKEN = "a-secure-test-token";

  @Mock private IdentityActionTokenRepository actions;
  @Mock private UserRepository users;
  @Mock private RefreshSessionRepository sessions;
  @Mock private AuthOutboxService outbox;
  @Mock private PasswordEncoder passwords;
  @Mock private AuthAuditService audit;
  @Mock private ActionTokenCodec actionTokens;
  @Mock private AuthAbuseProtection abuseProtection;
  @Mock private AuthService authService;

  private ActionTokenService service;
  private User user;

  @BeforeEach
  void setUp() {
    service = new ActionTokenService(actions, users, sessions, outbox, passwords, audit, actionTokens, abuseProtection, authService);
    user = User.builder()
        .id(UUID.fromString("10000000-0000-0000-0000-000000000001"))
        .name("Jane Doe")
        .email("jane@example.com")
        .emailNormalized("jane@example.com")
        .passwordHash("old-hash")
        .status(UserStatus.ACTIVE)
        .emailVerifiedAt(Instant.now().minusSeconds(60))
        .tokenVersion(0L)
        .build();
  }

  @Test
  void mintDeliveryTokenReturnsStableOpaqueTokenWithoutMutatingVerifier() throws Exception {
    stubDeliveryTokens();
    IdentityActionToken action = action(IdentityActionType.EMAIL_VERIFICATION, "old-token", user);
    when(actions.findById(action.getId())).thenReturn(Optional.of(action));

    DeliveryTokenResponse response = service.mintDeliveryToken(action.getId());

    assertThat(response.token()).isEqualTo("opaque-" + action.getId());
    assertThat(action.getTokenHash()).isEqualTo(hash("old-token"));
    verify(actionTokens).tokenFor(action.getId());
    verify(audit)
        .record(
            eq(null),
            eq(user.getId()),
            eq("ACTION_DELIVERY_TOKEN_MINTED"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            any());
  }

  @Test
  void confirmationConsumesActionActivatesUserAndWritesContactEvents() throws Exception {
    stubTokenHashes();
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setEmailVerifiedAt(null);
    IdentityActionToken action = action(IdentityActionType.EMAIL_VERIFICATION, RAW_TOKEN, user);
    when(actions.findByTokenHash(hash(RAW_TOKEN))).thenReturn(Optional.of(action));

    service.confirmVerification(actionRequest(RAW_TOKEN));

    assertThat(action.getConsumedAt()).isNotNull();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getEmailVerifiedAt()).isNotNull();
    verify(users).save(user);
    verify(outbox)
        .enqueue(
            eq("USER"),
            eq(user.getId()),
            eq("auth.user-email-verified.v1"),
            eq("auth.user-email-verified.v1"),
            eq(user.getId().toString()),
            any());
    verify(outbox).enqueueUserContactUpdated(user);
    verify(audit)
        .record(
            eq(user.getId()),
            eq(user.getId()),
            eq("EMAIL_VERIFIED"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            any());
  }

  @Test
  void resetPasswordConsumesActionRevokesSessionsAndIncrementsTokenVersion() throws Exception {
    stubTokenHashes();
    IdentityActionToken action = action(IdentityActionType.PASSWORD_RESET, RAW_TOKEN, user);
    RefreshSession session = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash("hash").tokenFamilyId(UUID.randomUUID()).expiresAt(Instant.now().plusSeconds(600)).build();
    when(actions.findByTokenHash(hash(RAW_TOKEN))).thenReturn(Optional.of(action));
    when(passwords.encode("new-password-123")).thenReturn("new-hash");
    when(sessions.findByUser_IdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(session));

    service.resetPassword(resetRequest(RAW_TOKEN));

    assertThat(action.getConsumedAt()).isNotNull();
    assertThat(user.getPasswordHash()).isEqualTo("new-hash");
    assertThat(user.getPasswordChangedAt()).isNotNull();
    assertThat(user.getTokenVersion()).isEqualTo(1L);
    assertThat(session.getRevokedAt()).isNotNull();
    verify(users).save(user);
    verify(outbox)
        .enqueue(
            eq("USER"),
            eq(user.getId()),
            eq("auth.password-reset.v1"),
            eq("auth.password-reset.v1"),
            eq(user.getId().toString()),
            any());
    verify(audit)
        .record(
            eq(user.getId()),
            eq(user.getId()),
            eq("PASSWORD_RESET"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            any());
  }

  @Test
  void passwordResetRequestIsSilentForUnknownUsersButAuditsWithoutPii() {
    ForgotPasswordRequest request = new ForgotPasswordRequest();
    request.setEmail("unknown@example.com");
    when(users.findByEmailNormalized("unknown@example.com")).thenReturn(Optional.empty());

    service.requestPasswordReset(request);

    verify(actions, never()).save(any());
    verify(outbox, never()).enqueue(any(), any(), any(), any(), any(), any());
    verify(audit)
        .record(
            eq(null),
            eq(null),
            eq("PASSWORD_RESET_REQUESTED"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            eq(java.util.Map.of("recipientKnown", false)));
  }

  @Test
  void resendVerificationReplacesPendingActionAndQueuesAnotherTokenFreeEvent() {
    stubDeliveryTokens();
    stubTokenHashes();
    user.setStatus(UserStatus.PENDING_VERIFICATION);
    user.setEmailVerifiedAt(null);
    IdentityActionToken prior = action(IdentityActionType.EMAIL_VERIFICATION, "prior", user);
    when(users.findByEmailNormalized("jane@example.com")).thenReturn(Optional.of(user));
    when(actions.findByUser_IdAndActionTypeAndConsumedAtIsNull(
            user.getId(), IdentityActionType.EMAIL_VERIFICATION))
        .thenReturn(List.of(prior));
    when(actions.save(any(IdentityActionToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ResendVerificationRequest request = new ResendVerificationRequest();
    request.setEmail("JANE@example.com");
    service.resendVerification(request, AuditRequestContext.empty());

    assertThat(prior.getConsumedAt()).isNotNull();
    verify(outbox)
        .enqueue(
            eq("USER"),
            eq(user.getId()),
            eq("auth.user-verification-requested.v1"),
            eq("auth.user-verification-requested.v1"),
            eq(user.getId().toString()),
            any());
  }

  @Test
  void emailChangeRequestConsumesPriorActionAndQueuesVerificationForNewAddress() {
    stubDeliveryTokens();
    stubTokenHashes();
    IdentityActionToken prior = action(IdentityActionType.EMAIL_CHANGE, "prior", user);
    UUID nextActionId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    when(users.existsByEmailNormalized("new@example.com")).thenReturn(false);
    when(actions.findByUser_IdAndActionTypeAndConsumedAtIsNull(user.getId(), IdentityActionType.EMAIL_CHANGE))
        .thenReturn(List.of(prior));
    when(actions.save(any(IdentityActionToken.class))).thenAnswer(invocation -> {
      IdentityActionToken next = invocation.getArgument(0);
      next.setId(nextActionId);
      return next;
    });

    service.requestEmailChange(user.getId(), emailChangeRequest(" New@Example.COM "), AuditRequestContext.empty());

    assertThat(prior.getConsumedAt()).isNotNull();
    ArgumentCaptor<IdentityActionToken> actionCaptor = ArgumentCaptor.forClass(IdentityActionToken.class);
    verify(actions).save(actionCaptor.capture());
    IdentityActionToken next = actionCaptor.getValue();
    assertThat(next.getActionType()).isEqualTo(IdentityActionType.EMAIL_CHANGE);
    assertThat(next.getTargetEmail()).isEqualTo("New@Example.COM");
    assertThat(next.getTokenHash()).startsWith("hash-opaque-");
    verify(outbox)
        .enqueue(
            eq("USER"),
            eq(user.getId()),
            eq("auth.email-change-requested.v1"),
            eq("auth.email-change-requested.v1"),
            eq(user.getId().toString()),
            any());
    verify(audit)
        .record(
            eq(user.getId()),
            eq(user.getId()),
            eq("EMAIL_CHANGE_REQUESTED"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            any());
  }

  @Test
  void emailChangeConfirmationUpdatesEmailInvalidatesSessionsAndPublishesContactContract()
      throws Exception {
    stubTokenHashes();
    IdentityActionToken action = action(IdentityActionType.EMAIL_CHANGE, RAW_TOKEN, user);
    action.setTargetEmail("new@example.com");
    RefreshSession session = RefreshSession.builder().id(UUID.randomUUID()).user(user)
        .tokenHash("hash").tokenFamilyId(UUID.randomUUID()).expiresAt(Instant.now().plusSeconds(600)).build();
    when(actions.findByTokenHash(hash(RAW_TOKEN))).thenReturn(Optional.of(action));
    when(sessions.findByUser_IdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(session));

    service.confirmEmailChange(actionRequest(RAW_TOKEN), AuditRequestContext.empty());

    assertThat(action.getConsumedAt()).isNotNull();
    assertThat(user.getEmail()).isEqualTo("new@example.com");
    assertThat(user.getEmailNormalized()).isEqualTo("new@example.com");
    assertThat(user.getTokenVersion()).isEqualTo(1L);
    assertThat(session.getRevokedAt()).isNotNull();
    verify(users).save(user);
    verify(outbox)
        .enqueue(
            eq("USER"),
            eq(user.getId()),
            eq("auth.user-email-changed.v1"),
            eq("auth.user-email-changed.v1"),
            eq(user.getId().toString()),
            any());
    verify(outbox).enqueueUserContactUpdated(user);
    verify(audit)
        .record(
            eq(null),
            eq(user.getId()),
            eq("EMAIL_CHANGE_CONFIRMED"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            any());
  }

  @Test
  void resetRejectsActionWithWrongType() throws Exception {
    stubTokenHashes();
    IdentityActionToken action = action(IdentityActionType.EMAIL_VERIFICATION, RAW_TOKEN, user);
    when(actions.findByTokenHash(hash(RAW_TOKEN))).thenReturn(Optional.of(action));

    assertThrows(UnauthorizedException.class, () -> service.resetPassword(resetRequest(RAW_TOKEN)));

    verify(users, never()).save(any());
  }

  private IdentityActionToken action(IdentityActionType type, String rawToken, User actionUser) {
    return IdentityActionToken.builder()
        .id(UUID.randomUUID())
        .user(actionUser)
        .tokenHash(hash(rawToken))
        .actionType(type)
        .expiresAt(Instant.now().plusSeconds(600))
        .build();
  }

  private ActionTokenRequest actionRequest(String token) {
    ActionTokenRequest request = new ActionTokenRequest();
    request.setToken(token);
    return request;
  }

  private ResetPasswordRequest resetRequest(String token) {
    ResetPasswordRequest request = new ResetPasswordRequest();
    request.setToken(token);
    request.setNewPassword("new-password-123");
    return request;
  }

  private EmailChangeRequest emailChangeRequest(String email) {
    EmailChangeRequest request = new EmailChangeRequest();
    request.setEmail(email);
    return request;
  }

  private String hash(String value) {
    return "hash-" + value;
  }

  private void stubDeliveryTokens() {
    when(actionTokens.tokenFor(any(UUID.class)))
        .thenAnswer(invocation -> "opaque-" + invocation.getArgument(0, UUID.class));
  }

  private void stubTokenHashes() {
    when(actionTokens.hash(anyString()))
        .thenAnswer(invocation -> hash(invocation.getArgument(0, String.class)));
  }
}
