package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.response.WebhookAckResponse;
import com.ecommerce.payment.enums.PaymentProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Validated
public interface PaymentWebhookService {

    WebhookAckResponse processWebhook(
            @NotNull PaymentProvider provider,
            @NotBlank String payload,
            @NotBlank String signature
    );
}