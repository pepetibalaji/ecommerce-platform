package com.ecommerce.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRefundRequest {

    @NotNull(message = "Payment id is required")
    private UUID paymentId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "Refund amount must have up to 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO format, for example USD or INR")
    private String currency;

    @NotBlank(message = "Refund reason is required")
    @Size(max = 5000, message = "Refund reason must not exceed 5000 characters")
    private String reason;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 150, message = "Idempotency key must not exceed 150 characters")
    private String idempotencyKey;
}