package com.ecommerce.payment.dto.request;

import com.ecommerce.payment.enums.PaymentProvider;
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
public class CreatePaymentRequest {

    @NotNull(message = "Order id is required")
    private UUID orderId;

    @NotNull(message = "User id is required")
    private UUID userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "Amount must have up to 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO format, for example USD or INR")
    private String currency;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 150, message = "Idempotency key must not exceed 150 characters")
    private String idempotencyKey;
}