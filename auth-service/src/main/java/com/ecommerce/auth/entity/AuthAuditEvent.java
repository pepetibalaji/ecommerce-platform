package com.ecommerce.auth.entity;

import com.ecommerce.auth.entity.enums.AuthAuditOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of security-sensitive Auth actions. Token and password material must never be
 * placed in {@link #metadata}.
 */
@Entity
@Table(name = "auth_audit_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuditEvent {

  @Id private UUID id;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "subject_user_id")
  private UUID subjectUserId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthAuditOutcome outcome;

  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "ip_address", columnDefinition = "inet")
  private String ipAddress;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
