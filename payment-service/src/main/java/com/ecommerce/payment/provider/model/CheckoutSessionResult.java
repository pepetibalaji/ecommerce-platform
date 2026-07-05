package com.ecommerce.payment.provider.model;

import com.ecommerce.payment.enums.PaymentProvider;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CheckoutSessionResult {

    private PaymentProvider provider;

    private String providerSessionId;

    private String providerPaymentIntentId;

    private String providerChargeId;

    private String checkoutUrl;

    private LocalDateTime expiresAt;
}