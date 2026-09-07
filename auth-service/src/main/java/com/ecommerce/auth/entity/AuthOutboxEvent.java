package com.ecommerce.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_outbox_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthOutboxEvent {
  @Id private UUID id;
  @Column(name = "aggregate_type", nullable = false) private String aggregateType;
  @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
  @Column(name = "event_type", nullable = false) private String eventType;
  @Column(nullable = false) private String topic;
  @Column(name = "event_key", nullable = false) private String eventKey;
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb") private String payload;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "published_at") private Instant publishedAt;
  @Column(nullable = false) private int attempts;
  @Column(name = "last_error") private String lastError;
  @Column(name = "next_attempt_at") private Instant nextAttemptAt;
  @Column(name = "lease_owner") private String leaseOwner;
  @Column(name = "lease_until") private Instant leaseUntil;
  @Column(name = "dead_lettered_at") private Instant deadLetteredAt;
  @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); }
}
