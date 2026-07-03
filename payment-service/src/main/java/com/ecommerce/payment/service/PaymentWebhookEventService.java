package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.request.CreatePaymentWebhookEventRequest;
import com.ecommerce.payment.dto.response.PaymentWebhookEventResponse;
import com.ecommerce.payment.enums.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Validated
public interface PaymentWebhookEventService {

    PaymentWebhookEventResponse createPaymentWebhookEvent(
            @Valid CreatePaymentWebhookEventRequest request
    );

    PaymentWebhookEventResponse getPaymentWebhookEventById(@NotNull UUID webhookEventId);

    PaymentWebhookEventResponse getPaymentWebhookEventByProviderEventId(
            @NotNull PaymentProvider provider,
            @NotBlank String providerEventId
    );

    boolean existsByProviderAndProviderEventId(
            @NotNull PaymentProvider provider,
            @NotBlank String providerEventId
    );

    PaymentWebhookEventResponse markProcessed(@NotNull UUID webhookEventId);

    PaymentWebhookEventResponse markIgnored(@NotNull UUID webhookEventId);

    PaymentWebhookEventResponse markFailed(@NotNull UUID webhookEventId);
}