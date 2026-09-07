package com.ecommerce.auth.service;

import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.entity.*;
import com.ecommerce.auth.entity.enums.*;
import com.ecommerce.auth.repository.*;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.events.topic.KafkaTopics;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RegistrationService {
  private final UserRepository users; private final RoleRepository roles; private final IdentityActionTokenRepository actions;
  private final AuthOutboxService outbox; private final PasswordEncoder passwordEncoder; private final AuthAuditService audit;
  private final ActionTokenCodec actionTokens;
  private final AuthAbuseProtection abuseProtection;
  @Transactional public void register(RegisterRequest request) { register(request, AuditRequestContext.empty()); }
  @Transactional public void register(RegisterRequest request, AuditRequestContext context) {
    abuseProtection.check("register", request.getEmail(), context);
    String normalized = request.getEmail().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmailNormalized(normalized)) {
      audit.recordAttempt(null, null, "REGISTERED", AuthAuditOutcome.DENIED, context,
          Map.of("reason", "email_already_registered"));
      throw new ResourceAlreadyExistsException("User already exists with this email");
    }
    com.ecommerce.auth.entity.enums.Role requestedRole =
        request.getRole() == null ? com.ecommerce.auth.entity.enums.Role.CUSTOMER : request.getRole();
    if (requestedRole != com.ecommerce.auth.entity.enums.Role.CUSTOMER) {
      throw new BadRequestException("Public registration creates customer accounts only");
    }
    com.ecommerce.auth.entity.Role role = roles.findByCode(requestedRole.name())
        .orElseThrow(() -> new IllegalStateException(requestedRole + " role is missing"));
    User user = User.builder().name(request.getName().trim()).email(request.getEmail().trim()).emailNormalized(normalized)
        .passwordHash(passwordEncoder.encode(request.getPassword())).status(UserStatus.PENDING_VERIFICATION).build();
    user.getRoles().add(role); users.save(user);
    UUID actionId = UUID.randomUUID();
    IdentityActionToken action = actions.save(IdentityActionToken.builder().id(actionId).user(user)
        .tokenHash(actionTokens.hash(actionTokens.tokenFor(actionId))).actionType(IdentityActionType.EMAIL_VERIFICATION)
        .expiresAt(Instant.now().plus(Duration.ofMinutes(30))).requestedIp(context.ipAddress()).userAgent(context.userAgent()).build());
    outbox.enqueue("USER", user.getId(), KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED, KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED,
        user.getId().toString(), Map.of("eventId", UUID.randomUUID(), "eventType", KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED,
            "userId", user.getId(), "email", user.getEmail(), "verificationActionId", action.getId(), "occurredAt", Instant.now()));
    audit.record(user.getId(), user.getId(), "REGISTERED", AuthAuditOutcome.SUCCESS, context, Map.of());
  }
}
