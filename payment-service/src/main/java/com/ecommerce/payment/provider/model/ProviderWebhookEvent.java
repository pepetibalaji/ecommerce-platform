package com.ecommerce.payment.provider.model;

import com.ecommerce.payment.enums.PaymentProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProviderWebhookEvent {

    private PaymentProvider provider;

    private String providerEventId;

    private String eventType;

    private ProviderPaymentStatus status;

    private String providerSessionId;

    private String providerPaymentIntentId;

    private String providerChargeId;

    private String failureReason;
}