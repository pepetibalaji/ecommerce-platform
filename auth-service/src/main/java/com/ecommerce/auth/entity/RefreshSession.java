package com.ecommerce.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshSession {
  @Id private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
  @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
  @Column(name = "token_family_id", nullable = false) private UUID tokenFamilyId;
  @Column(name = "session_started_at", nullable = false) private Instant sessionStartedAt;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "idle_expires_at", nullable = false) private Instant idleExpiresAt;
  @Column(name = "revoked_at") private Instant revokedAt;
  @Column(name = "replaced_by_session_id") private UUID replacedBySessionId;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "last_used_at") private Instant lastUsedAt;
  @Column(name = "device_name") private String deviceName;
  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "ip_address", columnDefinition = "inet")
  private String ipAddress;
  @Column(name = "user_agent") private String userAgent;
  @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); }
}
