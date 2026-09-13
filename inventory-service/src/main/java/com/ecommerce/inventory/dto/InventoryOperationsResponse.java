package com.ecommerce.inventory.dto;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
@Data @Builder public class InventoryOperationsResponse {
    private long pendingOutboxEvents;
    private long deadOutboxEvents;
    private Instant generatedAt;
}
