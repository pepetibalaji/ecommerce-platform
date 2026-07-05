package com.ecommerce.payment.provider.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefundPaymentResult {

    private String providerRefundId;

    private boolean successful;

    private String failureReason;
}