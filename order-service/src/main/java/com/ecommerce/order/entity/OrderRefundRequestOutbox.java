package com.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional hand-off for a full-payment refund requested as part of an order cancellation.
 * A row is retained after publishing so operations can prove what was requested and retry it.
 */
@Entity
@Table(name = "order_refund_request_outbox")
@Getter
@NoArgsConstructor
public class OrderRefundRequestOutbox {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefundRequestStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OrderRefundRequestOutbox(
            UUID orderId,
            UUID paymentId,
            UUID userId,
            UUID requestedBy,
            String actorType,
            BigDecimal amount,
            String currency,
            String reason,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.userId = userId;
        this.requestedBy = requestedBy;
        this.actorType = actorType;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.status = RefundRequestStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void published(Instant now) {
        status = RefundRequestStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void failed(Throwable error, Instant nextAttemptAt, int maxAttempts) {
        attemptCount++;
        lastError = safeError(error);
        status = attemptCount >= maxAttempts ? RefundRequestStatus.FAILED : RefundRequestStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
    }

    private String safeError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) {
            return error == null ? "Refund request publication failed" : error.getClass().getSimpleName();
        }
        return message.length() <= 2_000 ? message : message.substring(0, 2_000);
    }
}
