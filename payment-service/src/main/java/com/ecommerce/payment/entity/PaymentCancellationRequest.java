package com.ecommerce.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Durable command inbox doubles as a tombstone when cancellation overtakes payment preparation. */
@Entity
@Table(name = "payment_cancellation_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentCancellationRequest {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "actor_type", length = 40) private String actorType;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(columnDefinition = "TEXT") private String reason;
    @Column(name = "correlation_id", length = 128) private String correlationId;
    @Column(name = "trace_id", length = 128) private String traceId;
    @Column(name = "expiry_requested", nullable = false) private boolean expiryRequested;
    @Column(nullable = false, length = 40) private String status;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "completed_at") private Instant completedAt;
}
