package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity @Table(name = "inventory_event_outbox") @Getter @Setter @NoArgsConstructor
public class InventoryEventOutbox {
    public enum Status { PENDING, PROCESSING, PUBLISHED, DEAD }
    @Id private UUID id;
    @Column(nullable=false) private String topic;
    @Column(name="message_key", nullable=false) private String messageKey;
    @Lob @Column(nullable=false) private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
    @Column(nullable=false) private int attempts;
    private Instant nextAttemptAt; private Instant leaseUntil; private Instant publishedAt;
    private String lastError; @Column(nullable=false) private Instant createdAt;
    public InventoryEventOutbox(String topic, String key, String payload) { id=UUID.randomUUID(); this.topic=topic; messageKey=key; this.payload=payload; status=Status.PENDING; createdAt=Instant.now(); }
}
