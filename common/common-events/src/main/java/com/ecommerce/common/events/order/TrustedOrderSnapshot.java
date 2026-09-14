package com.ecommerce.common.events.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable checkout totals and current payable lifecycle. No address/customer profile data. */
public record TrustedOrderSnapshot(String schemaVersion, UUID orderId, UUID userId, String status,
        BigDecimal amount, String currency, UUID paymentId, Instant paymentExpiresAt) {}
