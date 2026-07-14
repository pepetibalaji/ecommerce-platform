package com.ecommerce.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminRefundRequest(
        @NotNull
        UUID orderId,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @NotBlank
        String currency,

        String reason,

        @NotBlank
        String idempotencyKey
) {
}