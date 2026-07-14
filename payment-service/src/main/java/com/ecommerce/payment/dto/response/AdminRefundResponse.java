package com.ecommerce.payment.dto.response;

import java.util.UUID;

public record AdminRefundResponse(
        UUID paymentId,
        UUID refundId,
        String status,
        String providerRefundId,
        String failureReason
) {
}