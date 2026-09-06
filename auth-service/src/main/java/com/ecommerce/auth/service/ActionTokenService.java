package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.*;
import com.ecommerce.auth.entity.*;
import com.ecommerce.auth.entity.enums.*;
import com.ecommerce.auth.repository.*;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import com.ecommerce.common.events.topic.KafkaTopics;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ActionTokenService {
  private final IdentityActionTokenRepository actions; private final UserRepository users; private final RefreshSessionRepository sessions;
  private final AuthOutboxService outbox; private final PasswordEncoder passwords; private final AuthAuditService audit;
  private final ActionTokenCodec actionTokens;

  @Transactional
  public DeliveryTokenResponse mintDeliveryToken(UUID actionId) {
    return mintDeliveryToken(actionId, AuditRequestContext.empty());
  }

  @Transactional
  public DeliveryTokenResponse mintDeliveryToken(UUID actionId, AuditRequestContext context) {
    IdentityActionToken action = actions.findById(actionId).orElse(null);
    if (action == null || !action.isUsableAt(Instant.now())) {
      audit.recordAttempt(null, null, "ACTION_DELIVERY_TOKEN_MINTED", AuthAuditOutcome.DENIED,
          context, Map.of("reason", "invalid_or_expired_action"));
      throw new UnauthorizedException("Invalid action");
    }
    String token = actionTokens.tokenFor(action.getId());
    audit.record(null, action.getUser().getId(), "ACTION_DELIVERY_TOKEN_MINTED", AuthAuditOutcome.SUCCESS,
        context, Map.of("actionType", action.getActionType().name()));
    return new DeliveryTokenResponse(token);
  }

  @Transactional public void confirmVerification(ActionTokenRequest request) { confirmVerification(request, AuditRequestContext.empty()); }

  @Transactional public void confirmVerification(ActionTokenRequest request, AuditRequestContext context) {
    IdentityActionToken action;
    try {
      action = usable(request.getToken(), IdentityActionType.EMAIL_VERIFICATION);
    } catch (UnauthorizedException exception) {
      audit.recordAttempt(null, null, "EMAIL_VERIFIED", AuthAuditOutcome.FAILURE, context,
          Map.of("reason", "invalid_or_expired_token"));
      throw exception;
    }
    User user = action.getUser();
    if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
      audit.recordAttempt(user.getId(), user.getId(), "EMAIL_VERIFIED", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "account_is_not_pending_verification"));
      throw new UnauthorizedException("Invalid or expired token");
    }
    Instant now = Instant.now();
    action.setConsumedAt(now); user.setStatus(UserStatus.ACTIVE); user.setEmailVerifiedAt(now);
    users.save(user); event(user, KafkaTopics.AUTH_USER_EMAIL_VERIFIED, KafkaTopics.AUTH_USER_EMAIL_VERIFIED); outbox.enqueueUserContactUpdated(user);
    audit.record(user.getId(), user.getId(), "EMAIL_VERIFIED", AuthAuditOutcome.SUCCESS, context, Map.of());
  }

  /** Does not reveal whether an address is registered or pending verification. */
  @Transactional
  public void resendVerification(ResendVerificationRequest request, AuditRequestContext context) {
    User user =
        users
            .findByEmailNormalized(normalize(request.getEmail()))
            .filter(candidate -> candidate.getStatus() == UserStatus.PENDING_VERIFICATION)
            .orElse(null);
    if (user == null) {
      audit.record(
          null,
          null,
          "EMAIL_VERIFICATION_RESEND_REQUESTED",
          AuthAuditOutcome.SUCCESS,
          context,
          Map.of("recipientKnown", false));
      return;
    }

    cancelOutstandingActions(user.getId(), IdentityActionType.EMAIL_VERIFICATION);
    IdentityActionToken action = action(user, IdentityActionType.EMAIL_VERIFICATION, null, context);
    outbox.enqueue(
        "USER",
        user.getId(),
        KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED,
        KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED,
        user.getId().toString(),
        Map.of(
            "eventId", UUID.randomUUID(),
            "eventType", KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED,
            "userId", user.getId(),
            "email", user.getEmail(),
            "verificationActionId", action.getId(),
            "occurredAt", Instant.now()));
    audit.record(
        user.getId(),
        user.getId(),
        "EMAIL_VERIFICATION_RESEND_REQUESTED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of("recipientKnown", true));
  }

  @Transactional public void requestPasswordReset(ForgotPasswordRequest request) { requestPasswordReset(request, AuditRequestContext.empty()); }

  @Transactional public void requestPasswordReset(ForgotPasswordRequest request, AuditRequestContext context) {
    User user = users.findByEmailNormalized(normalize(request.getEmail())).filter(User::isActiveAndVerified).orElse(null);
    if (user == null) {
      // Preserve the public response semantics while retaining an auditable, non-PII attempt.
      audit.record(null, null, "PASSWORD_RESET_REQUESTED", AuthAuditOutcome.SUCCESS, context,
          Map.of("recipientKnown", false));
      return;
    }
    cancelOutstandingActions(user.getId(), IdentityActionType.PASSWORD_RESET);
    IdentityActionToken action = action(user, IdentityActionType.PASSWORD_RESET, null, context);
    outbox.enqueue("USER", user.getId(), KafkaTopics.AUTH_PASSWORD_RESET_REQUESTED, KafkaTopics.AUTH_PASSWORD_RESET_REQUESTED,
        user.getId().toString(), Map.of("eventId", UUID.randomUUID(), "eventType", KafkaTopics.AUTH_PASSWORD_RESET_REQUESTED,
            "userId", user.getId(), "email", user.getEmail(), "passwordResetActionId", action.getId(), "occurredAt", Instant.now()));
    audit.record(user.getId(), user.getId(), "PASSWORD_RESET_REQUESTED", AuthAuditOutcome.SUCCESS, context, Map.of("recipientKnown", true));
  }

  @Transactional public void resetPassword(ResetPasswordRequest request) { resetPassword(request, AuditRequestContext.empty()); }

  @Transactional public void resetPassword(ResetPasswordRequest request, AuditRequestContext context) {
    IdentityActionToken action;
    try {
      action = usable(request.getToken(), IdentityActionType.PASSWORD_RESET);
    } catch (UnauthorizedException exception) {
      audit.recordAttempt(null, null, "PASSWORD_RESET", AuthAuditOutcome.FAILURE, context,
          Map.of("reason", "invalid_or_expired_token"));
      throw exception;
    }
    User user = action.getUser();
    if (!user.isActiveAndVerified()) {
      audit.recordAttempt(user.getId(), user.getId(), "PASSWORD_RESET", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "account_is_not_active"));
      throw new UnauthorizedException("Invalid or expired token");
    }
    action.setConsumedAt(Instant.now()); user.setPasswordHash(passwords.encode(request.getNewPassword())); user.setPasswordChangedAt(Instant.now()); user.setTokenVersion(user.getTokenVersion() + 1);
    sessions.findByUser_IdAndRevokedAtIsNull(user.getId()).forEach(s -> s.setRevokedAt(Instant.now())); users.save(user); event(user, "auth.password-reset.v1", "auth.password-reset.v1");
    audit.record(user.getId(), user.getId(), "PASSWORD_RESET", AuthAuditOutcome.SUCCESS, context, Map.of());
  }

  @Transactional
  public void requestEmailChange(UUID userId, EmailChangeRequest request, AuditRequestContext context) {
    User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if (!user.isActiveAndVerified()) {
      audit.recordAttempt(userId, userId, "EMAIL_CHANGE_REQUESTED", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "account_is_not_active"));
      throw new UnauthorizedException("Account is not active");
    }
    String email = request.getEmail().trim();
    String normalized = normalize(email);
    if (normalized.equals(user.getEmailNormalized())) {
      audit.recordAttempt(userId, userId, "EMAIL_CHANGE_REQUESTED", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "same_email"));
      throw new BadRequestException("New email must be different from the current email");
    }
    if (users.existsByEmailNormalized(normalized)) {
      audit.recordAttempt(userId, userId, "EMAIL_CHANGE_REQUESTED", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "email_already_in_use"));
      throw new ResourceAlreadyExistsException("Email is already in use");
    }

    cancelOutstandingActions(user.getId(), IdentityActionType.EMAIL_CHANGE);
    IdentityActionToken action = action(user, IdentityActionType.EMAIL_CHANGE, email, context);
    outbox.enqueue("USER", user.getId(), KafkaTopics.AUTH_EMAIL_CHANGE_REQUESTED, KafkaTopics.AUTH_EMAIL_CHANGE_REQUESTED,
        user.getId().toString(), Map.of("eventId", UUID.randomUUID(), "eventType", KafkaTopics.AUTH_EMAIL_CHANGE_REQUESTED,
            "userId", user.getId(), "email", email, "emailChangeActionId", action.getId(), "occurredAt", Instant.now()));
    audit.record(userId, userId, "EMAIL_CHANGE_REQUESTED", AuthAuditOutcome.SUCCESS, context, Map.of("targetEmail", email));
  }

  @Transactional
  public void confirmEmailChange(UUID userId, ActionTokenRequest request, AuditRequestContext context) {
    IdentityActionToken action;
    try {
      action = usable(request.getToken(), IdentityActionType.EMAIL_CHANGE);
    } catch (UnauthorizedException exception) {
      audit.recordAttempt(userId, null, "EMAIL_CHANGE_CONFIRMED", AuthAuditOutcome.FAILURE, context,
          Map.of("reason", "invalid_or_expired_token"));
      throw exception;
    }
    User user = action.getUser();
    if (!user.isActiveAndVerified()) {
      audit.recordAttempt(userId, user.getId(), "EMAIL_CHANGE_CONFIRMED", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "account_is_not_active"));
      throw new UnauthorizedException("Invalid or expired token");
    }
    if (!user.getId().equals(userId)) {
      audit.recordAttempt(userId, user.getId(), "EMAIL_CHANGE_CONFIRMED", AuthAuditOutcome.DENIED, context, Map.of());
      throw new UnauthorizedException("Invalid or expired token");
    }
    if (action.getTargetEmail() == null || action.getTargetEmail().isBlank()) {
      throw new UnauthorizedException("Invalid or expired token");
    }

    String newEmail = action.getTargetEmail().trim();
    String normalized = normalize(newEmail);
    if (!normalized.equals(user.getEmailNormalized()) && users.existsByEmailNormalized(normalized)) {
      throw new ResourceAlreadyExistsException("Email is already in use");
    }

    String previousEmail = user.getEmail();
    Instant now = Instant.now();
    action.setConsumedAt(now);
    user.setEmail(newEmail);
    user.setEmailNormalized(normalized);
    user.setEmailVerifiedAt(now);
    user.setTokenVersion(user.getTokenVersion() + 1);
    sessions.findByUser_IdAndRevokedAtIsNull(user.getId()).forEach(session -> session.setRevokedAt(now));
    users.save(user);

    outbox.enqueue("USER", user.getId(), KafkaTopics.AUTH_USER_EMAIL_CHANGED, KafkaTopics.AUTH_USER_EMAIL_CHANGED,
        user.getId().toString(), Map.of("eventId", UUID.randomUUID(), "eventType", "auth.user-email-changed.v1",
            "userId", user.getId(), "oldEmail", previousEmail, "newEmail", newEmail, "occurredAt", now));
    outbox.enqueueUserContactUpdated(user);
    audit.record(userId, user.getId(), "EMAIL_CHANGE_CONFIRMED", AuthAuditOutcome.SUCCESS, context,
        Map.of("previousEmail", previousEmail, "newEmail", newEmail));
  }

  private IdentityActionToken usable(String raw, IdentityActionType type) {
    IdentityActionToken action = actions.findByTokenHash(actionTokens.hash(raw)).orElseThrow(() -> new UnauthorizedException("Invalid or expired token"));
    if (action.getActionType() != type || !action.isUsableAt(Instant.now())) throw new UnauthorizedException("Invalid or expired token"); return action;
  }
  private IdentityActionToken action(
      User user, IdentityActionType type, String targetEmail, AuditRequestContext context) {
    UUID id = UUID.randomUUID();
    return actions.save(IdentityActionToken.builder().id(id).user(user)
        .tokenHash(actionTokens.hash(actionTokens.tokenFor(id))).actionType(type)
        .targetEmail(targetEmail).expiresAt(Instant.now().plus(Duration.ofMinutes(30)))
        .requestedIp(context.ipAddress()).userAgent(context.userAgent()).build());
  }
  private void cancelOutstandingActions(UUID userId, IdentityActionType type) {
    Instant now = Instant.now();
    actions.findByUser_IdAndActionTypeAndConsumedAtIsNull(userId, type).forEach(action -> action.setConsumedAt(now));
  }
  private void event(User user, String type, String topic) { outbox.enqueue("USER", user.getId(), type, topic, user.getId().toString(),
      Map.of("eventId", UUID.randomUUID(), "eventType", type, "userId", user.getId(), "email", user.getEmail(), "occurredAt", Instant.now())); }
  private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
