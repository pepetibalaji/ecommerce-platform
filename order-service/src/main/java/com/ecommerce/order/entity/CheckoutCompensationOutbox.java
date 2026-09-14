package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** Durable release command for a reservation made before checkout can persist an Order. */
@Entity @Table(name = "checkout_compensation_outbox") @Getter @NoArgsConstructor
public class CheckoutCompensationOutbox {
    @Id private UUID id;
    @Column(name="reservation_id", nullable=false, unique=true) private UUID reservationId;
    @Column(name="product_id", nullable=false) private UUID productId;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false, length=16) private String status;
    @Column(name="attempt_count", nullable=false) private int attemptCount;
    @Column(name="next_attempt_at", nullable=false) private Instant nextAttemptAt;
    @Column(name="last_error", columnDefinition="TEXT") private String lastError;
    public CheckoutCompensationOutbox(UUID reservationId, UUID productId, int quantity, Instant now) {
        id=UUID.randomUUID(); this.reservationId=reservationId; this.productId=productId; this.quantity=quantity;
        status="PENDING"; nextAttemptAt=now;
    }
    public void completed() { status="COMPLETED"; lastError=null; }
    public void failed(Throwable error, Instant next, int maxAttempts) {
        attemptCount++; lastError=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        status=attemptCount >= maxAttempts ? "FAILED" : "PENDING"; nextAttemptAt=next;
    }
}
