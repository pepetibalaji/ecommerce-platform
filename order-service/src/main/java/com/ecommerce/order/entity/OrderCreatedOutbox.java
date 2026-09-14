package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** Transactional hand-off for payment initiation. Kafka is never part of checkout's DB transaction. */
@Entity
@Table(name = "order_created_outbox")
@Getter
@NoArgsConstructor
public class OrderCreatedOutbox {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false, unique = true) private UUID orderId;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", columnDefinition = "TEXT") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;

    public OrderCreatedOutbox(UUID orderId, Instant now) {
        this.id = UUID.randomUUID(); this.orderId = orderId; this.status = "PENDING";
        this.nextAttemptAt = now; this.createdAt = now;
    }
    public void published(Instant now) { status = "PUBLISHED"; publishedAt = now; lastError = null; }
    public void failed(String error, Instant next, int maxAttempts) {
        attemptCount++; lastError = error == null ? "Kafka publish failed" : error.substring(0, Math.min(error.length(), 2000));
        status = attemptCount >= maxAttempts ? "FAILED" : "PENDING"; nextAttemptAt = next;
    }
}
