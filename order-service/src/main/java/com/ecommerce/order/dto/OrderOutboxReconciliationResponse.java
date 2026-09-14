package com.ecommerce.order.dto;

import java.time.Instant;

/** Read-only operational snapshot; individual outbox rows remain queryable directly from the service database. */
public record OrderOutboxReconciliationResponse(
        Instant observedAt,
        OutboxStatusCounts orderCreated,
        OutboxStatusCounts inventoryRelease,
        OutboxStatusCounts checkoutCompensation,
        OutboxStatusCounts refundRequest
) {}
