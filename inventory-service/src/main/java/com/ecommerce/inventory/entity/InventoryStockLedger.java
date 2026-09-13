package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable record of every stock counter transition. */
@Entity
@Table(name = "inventory_stock_ledger")
@Getter
@NoArgsConstructor
public class InventoryStockLedger {
    @Id private UUID id;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "seller_id") private UUID sellerId;
    @Column(nullable = false) private int adjustment;
    @Column(name = "previous_available_stock", nullable = false) private int previousAvailableStock;
    @Column(name = "new_available_stock", nullable = false) private int newAvailableStock;
    @Column(name = "previous_reserved_stock", nullable = false) private int previousReservedStock;
    @Column(name = "new_reserved_stock", nullable = false) private int newReservedStock;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private StockAdjustmentReason reason;
    @Column(name = "actor_identity", nullable = false) private String actorIdentity;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    public InventoryStockLedger(Inventory inventory, int adjustment, StockAdjustmentReason reason,
            String actorIdentity, UUID referenceId) {
        this.id = UUID.randomUUID(); this.productId = inventory.getProductId(); this.sellerId = inventory.getSellerId();
        this.adjustment = adjustment; this.previousAvailableStock = inventory.getAvailableStock() - adjustment;
        this.newAvailableStock = inventory.getAvailableStock(); this.previousReservedStock = inventory.getReservedStock();
        this.newReservedStock = inventory.getReservedStock(); this.reason = reason; this.actorIdentity = actorIdentity;
        this.referenceId = referenceId; this.recordedAt = Instant.now();
    }
}
