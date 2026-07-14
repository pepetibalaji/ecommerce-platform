package com.ecommerce.payment.provider.model;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundGatewayRequest(
        UUID paymentId,
        UUID orderId,
        String providerPaymentIntentId,
        BigDecimal amount,
        String currency,
        String reason,
        String idempotencyKey
) {
}