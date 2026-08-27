package com.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_inventory_release_outbox")
@Getter
@NoArgsConstructor
public class InventoryReleaseOutbox {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private UUID reservationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InventoryReleaseReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InventoryReleaseStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public InventoryReleaseOutbox(
            UUID orderId,
            UUID orderItemId,
            UUID reservationId,
            UUID productId,
            Integer quantity,
            InventoryReleaseReason reason
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.reservationId = reservationId;
        this.productId = productId;
        this.quantity = quantity;
        this.reason = reason;
        this.status = InventoryReleaseStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markCompleted(LocalDateTime now) {
        status = InventoryReleaseStatus.COMPLETED;
        lastError = null;
        completedAt = now;
        updatedAt = now;
    }

    public void recordFailure(String error, LocalDateTime nextAttemptAt, int maxAttempts, LocalDateTime now) {
        attemptCount++;
        lastError = truncate(error);
        updatedAt = now;
        if (attemptCount >= maxAttempts) {
            status = InventoryReleaseStatus.FAILED;
            this.nextAttemptAt = now;
        } else {
            this.nextAttemptAt = nextAttemptAt;
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Inventory release failed without an error message";
        }

        return value.length() <= 2_000 ? value : value.substring(0, 2_000);
    }
}
