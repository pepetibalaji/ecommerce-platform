package com.ecommerce.order.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable checkout claim.  It is deliberately independent of {@code orders} so a concurrent
 * request can be serialized before it makes any remote inventory reservation.
 */
@Entity
@Table(
        name = "order_idempotency_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_idempotency_records_user_key",
                columnNames = {"user_id", "idempotency_key"})
)
@Getter
@NoArgsConstructor
public class OrderIdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public void complete(UUID resultingOrderId) {
        this.orderId = resultingOrderId;
        this.status = "COMPLETED";
    }
}
