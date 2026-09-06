package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.ReplaceRolesRequest;
import com.ecommerce.auth.dto.SessionResponse;
import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UpdateStatusRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.RefreshSessionRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final UserRepository users;
  private final RoleRepository roles;
  private final RefreshSessionRepository sessions;
  private final AuthService auth;
  private final PasswordEncoder passwords;
  private final AuthAuditService audit;
  private final AuthOutboxService outbox;

  @Transactional(readOnly = true)
  public UserProfileResponse me(UUID id) {
    return profile(user(id));
  }

  @Transactional
  public List<SessionResponse> sessions(UUID id) {
    return sessions(id, AuditRequestContext.empty());
  }

  @Transactional
  public List<SessionResponse> sessions(UUID id, AuditRequestContext context) {
    user(id);
    Instant now = Instant.now();
    List<SessionResponse> result = sessions.findByUser_IdAndRevokedAtIsNull(id).stream()
        .filter(session -> session.getExpiresAt().isAfter(now))
        .map(
            session ->
                new SessionResponse(
                    session.getId(),
                    session.getCreatedAt(),
                    session.getLastUsedAt(),
                    session.getExpiresAt(),
                    session.getDeviceName(),
                    session.getIpAddress(),
                    session.getUserAgent()))
        .toList();
    audit.record(
        id,
        id,
        "SESSIONS_LISTED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of("resultCount", result.size()));
    return result;
  }

  @Transactional
  public void revokeSession(UUID userId, UUID sessionId, AuditRequestContext context) {
    var session =
        sessions
            .findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    if (!session.getUser().getId().equals(userId)) {
      audit.recordAttempt(
          userId,
          session.getUser().getId(),
          "SESSION_REVOKED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of("reason", "session_owner_mismatch"));
      throw new ResourceNotFoundException("Session not found");
    }
    if (session.getRevokedAt() == null) {
      session.setRevokedAt(Instant.now());
    }
    audit.record(userId, userId, "SESSION_REVOKED", AuthAuditOutcome.SUCCESS, context, Map.of());
  }

  @Transactional
  public UserProfileResponse update(UUID id, UpdateMeRequest request) {
    return update(id, request, AuditRequestContext.empty());
  }

  @Transactional
  public UserProfileResponse update(UUID id, UpdateMeRequest request, AuditRequestContext context) {
    User user = user(id);
    user.setName(request.getName().trim());
    audit.record(id, id, "PROFILE_UPDATED", AuthAuditOutcome.SUCCESS, context, Map.of());
    return profile(user);
  }

  @Transactional
  public void password(UUID id, ChangePasswordRequest request) {
    password(id, request, AuditRequestContext.empty());
  }

  @Transactional
  public void password(UUID id, ChangePasswordRequest request, AuditRequestContext context) {
    User user = user(id);
    if (!passwords.matches(request.getCurrentPassword(), user.getPasswordHash())) {
      audit.recordAttempt(
          id,
          id,
          "PASSWORD_CHANGE",
          AuthAuditOutcome.FAILURE,
          context,
          Map.of("reason", "invalid_current_password"));
      throw new UnauthorizedException("Invalid credentials");
    }

    user.setPasswordHash(passwords.encode(request.getNewPassword()));
    user.setPasswordChangedAt(Instant.now());
    user.setTokenVersion(user.getTokenVersion() + 1);
    auth.revokeAll(id);
    audit.record(id, id, "PASSWORD_CHANGE", AuthAuditOutcome.SUCCESS, context, Map.of());
  }

  /** Administrative lookup. It deliberately writes a durable audit event. */
  @Transactional
  public List<AdminUserResponse> list(UUID actorUserId, AuditRequestContext context) {
    List<AdminUserResponse> result = users.findAll().stream().map(this::admin).toList();
    audit.record(
        actorUserId,
        null,
        "ADMIN_USERS_LISTED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of("resultCount", result.size()));
    return result;
  }

  /** Compatibility entry point for callers that have not yet supplied an actor context. */
  @Transactional
  public List<AdminUserResponse> list() {
    return list(null, AuditRequestContext.empty());
  }

  @Transactional
  public AdminUserResponse get(UUID id, UUID actorUserId, AuditRequestContext context) {
    AdminUserResponse response = admin(user(id));
    audit.record(
        actorUserId,
        id,
        "ADMIN_USER_VIEWED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of());
    return response;
  }

  @Transactional
  public void status(UUID id, UpdateStatusRequest request) {
    status(id, request, id, AuditRequestContext.empty());
  }

  @Transactional
  public void status(
      UUID id, UpdateStatusRequest request, UUID actorUserId, AuditRequestContext context) {
    User user = user(id);
    if (user.getStatus() == UserStatus.DELETED && request.getStatus() != UserStatus.DELETED) {
      audit.recordAttempt(
          actorUserId,
          id,
          "USER_STATUS_UPDATED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of("reason", "deleted_accounts_cannot_be_reactivated"));
      throw new BadRequestException("Deleted accounts cannot be reactivated");
    }
    if (request.getStatus() == UserStatus.PENDING_VERIFICATION
        && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
      audit.recordAttempt(
          actorUserId,
          id,
          "USER_STATUS_UPDATED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of("reason", "verification_state_cannot_be_restored"));
      throw new BadRequestException("The pending-verification state is managed by email confirmation");
    }
    if (user.getStatus() == UserStatus.PENDING_VERIFICATION
        && request.getStatus() == UserStatus.SUSPENDED) {
      audit.recordAttempt(
          actorUserId,
          id,
          "USER_STATUS_UPDATED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of("reason", "pending_account_cannot_be_suspended"));
      throw new BadRequestException("A pending account may only be verified or deleted");
    }
    if (request.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() == null) {
      audit.recordAttempt(
          actorUserId,
          id,
          "USER_STATUS_UPDATED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of("reason", "email_not_verified"));
      throw new BadRequestException("An account cannot be activated before email verification");
    }
    String previousStatus = user.getStatus().name();
    user.setStatus(request.getStatus());
    if (request.getStatus() == UserStatus.DELETED && user.getDeletedAt() == null) {
      user.setDeletedAt(Instant.now());
    }
    // Any status transition invalidates issued access-token version and all refresh sessions.
    user.setTokenVersion(user.getTokenVersion() + 1);
    auth.revokeAll(id);
    outbox.enqueueUserContactUpdated(user);
    audit.record(
        actorUserId,
        id,
        "USER_STATUS_UPDATED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of("previousStatus", previousStatus, "newStatus", request.getStatus().name()));
  }

  @Transactional
  public void roles(UUID id, ReplaceRolesRequest request) {
    roles(id, request, id, AuditRequestContext.empty());
  }

  @Transactional
  public void roles(
      UUID id, ReplaceRolesRequest request, UUID actorUserId, AuditRequestContext context) {
    User user = user(id);
    var assigned = roles.findByCodeIn(request.getRoles());
    if (assigned.size() != request.getRoles().size()) {
      audit.recordAttempt(
          actorUserId,
          id,
          "USER_ROLES_UPDATED",
          AuthAuditOutcome.DENIED,
          context,
          Map.of("reason", "unknown_role"));
      throw new ResourceNotFoundException("Unknown role");
    }
    user.setRoles(new HashSet<>(assigned));
    user.setTokenVersion(user.getTokenVersion() + 1);
    auth.revokeAll(id);
    audit.record(
        actorUserId,
        id,
        "USER_ROLES_UPDATED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of("roles", assigned.stream().map(role -> role.getCode()).sorted().toList()));
  }

  @Transactional
  public void forceLogout(UUID id, UUID actorUserId, AuditRequestContext context) {
    user(id);
    auth.revokeAll(id);
    audit.record(
        actorUserId,
        id,
        "USER_SESSIONS_REVOKED",
        AuthAuditOutcome.SUCCESS,
        context,
        Map.of());
  }

  private User user(UUID id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  private UserProfileResponse profile(User user) {
    return new UserProfileResponse(
        user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getStatus());
  }

  private AdminUserResponse admin(User user) {
    return new AdminUserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
