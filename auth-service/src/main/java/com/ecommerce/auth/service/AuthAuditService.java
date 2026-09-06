package com.ecommerce.auth.service;

import com.ecommerce.auth.entity.AuthAuditEvent;
import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import com.ecommerce.auth.repository.AuthAuditEventRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthAuditService {

  private final AuthAuditEventRepository events;

  /**
   * Adds an audit row to the caller's transaction so successful state changes and their audit
   * entries commit atomically.
   */
  @Transactional
  public void record(
      UUID actorUserId,
      UUID subjectUserId,
      String eventType,
      AuthAuditOutcome outcome,
      AuditRequestContext context,
      Map<String, Object> metadata) {
    events.save(event(actorUserId, subjectUserId, eventType, outcome, context, metadata));
  }

  /**
   * Persists rejected or failed attempts independently because the calling transaction commonly
   * ends by throwing an exception.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordAttempt(
      UUID actorUserId,
      UUID subjectUserId,
      String eventType,
      AuthAuditOutcome outcome,
      AuditRequestContext context,
      Map<String, Object> metadata) {
    events.save(event(actorUserId, subjectUserId, eventType, outcome, context, metadata));
  }

  private AuthAuditEvent event(
      UUID actorUserId,
      UUID subjectUserId,
      String eventType,
      AuthAuditOutcome outcome,
      AuditRequestContext context,
      Map<String, Object> metadata) {
    AuditRequestContext safeContext = context == null ? AuditRequestContext.empty() : context;
    return AuthAuditEvent.builder()
        .actorUserId(actorUserId)
        .subjectUserId(subjectUserId)
        .eventType(eventType)
        .outcome(outcome)
        .ipAddress(safeContext.ipAddress())
        .userAgent(safeContext.userAgent())
        .metadata(metadata == null || metadata.isEmpty() ? null : Map.copyOf(metadata))
        .build();
  }
}
