package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID paymentId;

    private UUID orderId;

    private UUID userId;

    private BigDecimal amount;

    private String currency;

    private PaymentStatus status;

    private PaymentProvider provider;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String failureReason;

    private Instant createdAt;

    private Instant updatedAt;
}
