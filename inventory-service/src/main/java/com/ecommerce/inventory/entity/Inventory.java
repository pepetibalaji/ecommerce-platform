package com.ecommerce.inventory.entity;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "seller_id")
    private UUID sellerId;

    @Builder.Default
    @Column(name = "product_active", nullable = false)
    private boolean productActive = true;

    @Column(name = "product_version", nullable = false)
    private long productVersion;

    @Column(name = "last_product_event_id")
    private UUID lastProductEventId;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private Integer reservedStock;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
