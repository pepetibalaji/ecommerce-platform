package com.ecommerce.common.events.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Versioned catalogue snapshot. Product versions increase on each mutation; reconciliation may
 * republish the current version under a new event id. Consumers must ignore versions already applied.
 * Price is catalogue data only and does not transfer ownership of stock or checkout validation.
 */
public record ProductLifecycleEvent(
        UUID eventId,
        UUID productId,
        UUID sellerId,
        Instant occurredAt,
        String eventType,
        int schemaVersion,
        long productVersion,
        boolean active,
        String name,
        BigDecimal price,
        String currency,
        UUID actorUserId,
        String description,
        String category,
        String brand,
        List<String> imageUrls) {
}
