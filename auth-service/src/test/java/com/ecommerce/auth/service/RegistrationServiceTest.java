package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.IdentityActionToken;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.entity.enums.IdentityActionType;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.IdentityActionTokenRepository;
import com.ecommerce.auth.repository.RoleRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import java.time.Instant;
import java.util.Map;
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
class RegistrationServiceTest {

  @Mock private UserRepository users;
  @Mock private RoleRepository roles;
  @Mock private IdentityActionTokenRepository actions;
  @Mock private AuthOutboxService outbox;
  @Mock private PasswordEncoder passwords;
  @Mock private AuthAuditService audit;
  @Mock private ActionTokenCodec actionTokens;

  private RegistrationService service;

  @BeforeEach
  void setUp() {
    service = new RegistrationService(users, roles, actions, outbox, passwords, audit, actionTokens);
  }

  @Test
  void registerCreatesPendingUserHashedVerificationActionAndOutboxEvent() {
    Role customer = role("CUSTOMER");
    UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    when(users.existsByEmailNormalized("jane@example.com")).thenReturn(false);
    when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(customer));
    when(passwords.encode("Password@123")).thenReturn("argon2-hash");
    when(actionTokens.tokenFor(any(UUID.class))).thenReturn("opaque-delivery-token");
    when(actionTokens.hash("opaque-delivery-token")).thenReturn("delivery-token-hash");
    when(users.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(userId);
      return user;
    });
    when(actions.save(any(IdentityActionToken.class))).thenAnswer(invocation -> {
      return invocation.getArgument(0);
    });

    service.register(registerRequest(" Jane Doe ", " JANE@Example.COM "));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(users).save(userCaptor.capture());
    User user = userCaptor.getValue();
    assertThat(user.getName()).isEqualTo("Jane Doe");
    assertThat(user.getEmail()).isEqualTo("JANE@Example.COM");
    assertThat(user.getEmailNormalized()).isEqualTo("jane@example.com");
    assertThat(user.getPasswordHash()).isEqualTo("argon2-hash");
    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    assertThat(user.getEmailVerifiedAt()).isNull();
    assertThat(user.getRoles()).containsExactly(customer);

    ArgumentCaptor<IdentityActionToken> actionCaptor =
        ArgumentCaptor.forClass(IdentityActionToken.class);
    verify(actions).save(actionCaptor.capture());
    IdentityActionToken action = actionCaptor.getValue();
    assertThat(action.getActionType()).isEqualTo(IdentityActionType.EMAIL_VERIFICATION);
    assertThat(action.getTokenHash()).isEqualTo("delivery-token-hash");
    assertThat(action.getExpiresAt()).isAfter(Instant.now());
    verify(actionTokens).tokenFor(action.getId());
    verify(actionTokens).hash("opaque-delivery-token");

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outbox)
        .enqueue(
            eq("USER"),
            eq(userId),
            eq("auth.user-verification-requested.v1"),
            eq("auth.user-verification-requested.v1"),
            eq(userId.toString()),
            payloadCaptor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
    assertThat(payload)
        .containsEntry("userId", userId)
        .containsEntry("verificationActionId", action.getId())
        .doesNotContainKey("token");
    verify(audit)
        .record(
            eq(userId),
            eq(userId),
            eq("REGISTERED"),
            eq(AuthAuditOutcome.SUCCESS),
            any(AuditRequestContext.class),
            eq(Map.of()));
  }

  @Test
  void registerRejectsExistingNormalizedEmailWithoutPersistingAnything() {
    when(users.existsByEmailNormalized("jane@example.com")).thenReturn(true);

    assertThrows(
        ResourceAlreadyExistsException.class,
        () -> service.register(registerRequest("Jane Doe", "jane@example.com")));

    verify(users, never()).save(any());
    verify(actions, never()).save(any());
    verify(outbox, never()).enqueue(any(), any(), any(), any(), any(), any());
    verify(audit, never()).record(any(), any(), any(), any(), any(), any());
  }

  private RegisterRequest registerRequest(String name, String email) {
    RegisterRequest request = new RegisterRequest();
    request.setName(name);
    request.setEmail(email);
    request.setPassword("Password@123");
    return request;
  }

  private Role role(String code) {
    Role role = new Role();
    role.setId(UUID.randomUUID());
    role.setCode(code);
    return role;
  }
}
