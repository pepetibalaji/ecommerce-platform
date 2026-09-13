package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_reservation_audit")
@Getter
@NoArgsConstructor
public class InventoryReservationAudit {
    @Id private UUID id;
    @Column(name = "reservation_id", nullable = false) private UUID reservationId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20) private InventoryReservationStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 20) private InventoryReservationStatus toStatus;
    @Column(name = "actor_identity", nullable = false) private String actorIdentity;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;
    public InventoryReservationAudit(InventoryReservation reservation, InventoryReservationStatus fromStatus,
            String actorIdentity) { this.id = UUID.randomUUID(); this.reservationId = reservation.getId();
        this.productId = reservation.getProductId(); this.fromStatus = fromStatus; this.toStatus = reservation.getStatus();
        this.actorIdentity = actorIdentity; this.recordedAt = Instant.now(); }
}
