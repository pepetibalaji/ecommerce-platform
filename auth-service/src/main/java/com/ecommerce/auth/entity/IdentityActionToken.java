package com.ecommerce.auth.entity;

import com.ecommerce.auth.entity.enums.IdentityActionType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "identity_action_tokens")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class IdentityActionToken {
  @Id private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
  @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
  @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false) private IdentityActionType actionType;
  @Column(name = "target_email") private String targetEmail;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "consumed_at") private Instant consumedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "requested_ip", columnDefinition = "inet")
  private String requestedIp;
  @Column(name = "user_agent") private String userAgent;
  @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); }
  public boolean isUsableAt(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
}
