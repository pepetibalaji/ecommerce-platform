package com.ecommerce.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderLifecycleAuditResponse(
        UUID id,
        String action,
        UUID actorId,
        String actorType,
        String reason,
        UUID refundRequestId,
        Instant createdAt
) {}
