package com.ecommerce.payment.service;

import java.time.Instant;
import java.util.UUID;

/** Audit context belongs to the durable refund command and survives worker restarts. */
public record RefundAudit(UUID refundRequestId, UUID requestedBy, String actorType,
                          String correlationId, String traceId, Instant requestedAt) {
    public static RefundAudit system() {
        return new RefundAudit(null, null, "SYSTEM", org.slf4j.MDC.get("correlationId"),
                org.slf4j.MDC.get("traceId"), Instant.now());
    }
}
