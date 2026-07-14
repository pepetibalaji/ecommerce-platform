package com.ecommerce.payment.provider.model;

public record RefundGatewayResponse(
        boolean success,
        String providerRefundId,
        String status,
        String failureReason
) {
}