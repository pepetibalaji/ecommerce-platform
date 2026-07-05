package com.ecommerce.payment.provider.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class RefundPaymentCommand {

    private UUID paymentId;

    private UUID refundId;

    private BigDecimal amount;

    private String currency;

    private String providerPaymentIntentId;

    private String providerChargeId;

    private String reason;

    private String idempotencyKey;
}