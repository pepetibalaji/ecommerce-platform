package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import jakarta.validation.constraints.NotBlank;
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
public class PaymentWebhookEventResponse {

    @NotNull(message = "Webhook event id is required")
    private UUID id;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    @NotBlank(message = "Provider event id is required")
    @Size(max = 255, message = "Provider event id must not exceed 255 characters")
    private String providerEventId;

    private UUID paymentId;

    @NotBlank(message = "Event type is required")
    @Size(max = 150, message = "Event type must not exceed 150 characters")
    private String eventType;

    @NotNull(message = "Webhook processing status is required")
    private WebhookProcessingStatus processingStatus;

    @Size(max = 128, message = "Payload hash must not exceed 128 characters")
    private String payloadHash;

    @NotNull(message = "Received timestamp is required")
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}