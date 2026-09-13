package com.ecommerce.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
@Getter
@NoArgsConstructor
public class InventoryReservation {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public InventoryReservation(UUID id, UUID productId, Integer quantity, Instant expiresAt) {
        Instant now = Instant.now();
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.status = InventoryReservationStatus.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = expiresAt;
    }

    public void release() {
        status = InventoryReservationStatus.RELEASED;
        updatedAt = Instant.now();
    }

    public void deduct() {
        status = InventoryReservationStatus.DEDUCTED;
        updatedAt = Instant.now();
    }
}
