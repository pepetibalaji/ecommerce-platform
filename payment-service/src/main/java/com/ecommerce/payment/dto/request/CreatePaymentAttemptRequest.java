package com.ecommerce.payment.dto.request;

import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentAttemptRequest {

    @NotNull(message = "Payment id is required")
    private UUID paymentId;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    @Size(max = 255, message = "Provider session id must not exceed 255 characters")
    private String providerSessionId;

    @Size(max = 255, message = "Provider payment intent id must not exceed 255 characters")
    private String providerPaymentIntentId;

    @Size(max = 255, message = "Provider charge id must not exceed 255 characters")
    private String providerChargeId;

    @Size(max = 5000, message = "Checkout URL must not exceed 5000 characters")
    private String checkoutUrl;

    @NotNull(message = "Payment attempt status is required")
    private PaymentAttemptStatus status;

    @Size(max = 5000, message = "Failure reason must not exceed 5000 characters")
    private String failureReason;

    @Future(message = "Expiration timestamp must be in the future")
    private LocalDateTime expiresAt;
}