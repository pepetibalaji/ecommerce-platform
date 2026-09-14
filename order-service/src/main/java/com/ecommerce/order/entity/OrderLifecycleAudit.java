package com.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Immutable operational audit trail for cancellation and refund decisions. */
@Entity
@Table(name = "order_lifecycle_audit")
@Getter
@NoArgsConstructor
public class OrderLifecycleAudit {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_type", nullable = false, length = 32)
    private String actorType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "refund_request_id")
    private UUID refundRequestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OrderLifecycleAudit(
            UUID orderId,
            String action,
            UUID actorId,
            String actorType,
            String reason,
            UUID refundRequestId,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.action = action;
        this.actorId = actorId;
        this.actorType = actorType;
        this.reason = reason;
        this.refundRequestId = refundRequestId;
        this.createdAt = createdAt;
    }
}
