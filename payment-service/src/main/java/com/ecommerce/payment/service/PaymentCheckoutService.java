package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.response.CreateCheckoutSessionResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Validated
public interface PaymentCheckoutService {

    CreateCheckoutSessionResponse createCheckoutSession(
            @NotNull(message = "Order id is required") UUID orderId,
            @NotNull(message = "User id is required") UUID userId
    );
}