package com.ecommerce.payment.provider.model;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

import com.ecommerce.payment.dto.response.ProviderRefundStatus;
import com.ecommerce.payment.enums.PaymentProvider;

@Getter
@Builder(toBuilder = true)
public class ProviderWebhookEvent {

    private PaymentProvider provider;

    private String providerEventId;

    private java.util.UUID paymentId;

    private String attemptIdempotencyKey;

    private BigDecimal amount;

    private String currency;

    private String eventType;

    private ProviderPaymentStatus status;

    private ProviderRefundStatus refundStatus;

    private String providerSessionId;

    private String providerPaymentIntentId;

    private String providerChargeId;

    private String providerRefundId;

    private BigDecimal refundAmount;

    private String failureReason;
}
