package com.ecommerce.payment.provider.sandbox;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.dto.response.ProviderRefundStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.provider.model.RefundGatewayRequest;
import com.ecommerce.payment.provider.model.RefundGatewayResponse;
import com.ecommerce.payment.provider.model.RefundPaymentCommand;
import com.ecommerce.payment.provider.model.RefundPaymentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SandboxPaymentGateway implements PaymentGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PaymentProviderProperties properties;

    @Override
    public boolean supports(PaymentProvider provider) {
        return PaymentProvider.SANDBOX == provider;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProvider().getSandbox().isEnabled();
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand command) {
        String checkoutUrl = "http://localhost:3001/mock-checkout"
                + "?paymentId=" + command.getPaymentId()
                + "&orderId=" + command.getOrderId();

        return CheckoutSessionResult.builder()
                .provider(PaymentProvider.SANDBOX)
                .providerSessionId("sandbox-session-" + command.getPaymentId())
                .providerPaymentIntentId("sandbox-intent-" + command.getPaymentId())
                .checkoutUrl(checkoutUrl)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    @Override
    public void verifyWebhookSignature(String payload, String signature) {
        String expected = properties.getProvider().getSandbox().getWebhookSecret();
        if (signature == null || signature.isBlank() || !signature.equals(expected)) {
            throw new BadRequestException("Invalid sandbox webhook signature");
        }
    }

    @Override
    public ProviderWebhookEvent parseWebhookEvent(String payload, String signature) {
        verifyWebhookSignature(payload, signature);

        try {
            JsonNode event = OBJECT_MAPPER.readTree(payload);
            String eventId = required(event, "eventId");
            String eventType = required(event, "eventType").toLowerCase(Locale.ROOT);
            ProviderRefundStatus refundStatus = refundStatus(eventType);

            return ProviderWebhookEvent.builder()
                    .provider(PaymentProvider.SANDBOX)
                    .providerEventId(eventId)
                    .eventType(eventType)
                    .status(paymentStatus(eventType, refundStatus))
                    .refundStatus(refundStatus)
                    .providerSessionId(text(event, "providerSessionId"))
                    .providerPaymentIntentId(text(event, "providerPaymentIntentId"))
                    .providerChargeId(text(event, "providerChargeId"))
                    .providerRefundId(text(event, "providerRefundId"))
                    .failureReason(text(event, "failureReason"))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Invalid sandbox webhook payload");
        }
    }

    @Override
    public ProviderWebhookEvent getPaymentStatus(String providerPaymentId) {
        return ProviderWebhookEvent.builder()
                .provider(PaymentProvider.SANDBOX)
                .providerEventId("sandbox-status-" + providerPaymentId)
                .eventType("sandbox.status")
                .status(ProviderPaymentStatus.PROCESSING)
                .providerPaymentIntentId(providerPaymentId)
                .build();
    }

    @Override
    public RefundPaymentResult refundPayment(RefundPaymentCommand command) {
        return RefundPaymentResult.builder()
                .successful(false)
                .failureReason("Use refund(RefundGatewayRequest) for PAYMENT-103 refund flow")
                .build();
    }

    @Override
    public RefundGatewayResponse refund(RefundGatewayRequest request) {
        return new RefundGatewayResponse(
                true,
                "sandbox-refund-" + request.paymentId(),
                "processing",
                null
        );
    }

    private String required(JsonNode event, String field) {
        String value = text(event, field);
        if (value == null) {
            throw new BadRequestException("Sandbox webhook " + field + " is required");
        }
        return value;
    }

    private String text(JsonNode event, String field) {
        JsonNode value = event.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private ProviderPaymentStatus paymentStatus(String eventType, ProviderRefundStatus refundStatus) {
        if (refundStatus != null) {
            return ProviderPaymentStatus.IGNORED;
        }
        return switch (eventType) {
            case "payment.succeeded", "checkout.session.completed" -> ProviderPaymentStatus.SUCCESS;
            case "payment.failed" -> ProviderPaymentStatus.FAILED;
            case "payment.cancelled", "checkout.session.expired" -> ProviderPaymentStatus.CANCELLED;
            case "payment.processing" -> ProviderPaymentStatus.PROCESSING;
            default -> ProviderPaymentStatus.IGNORED;
        };
    }

    private ProviderRefundStatus refundStatus(String eventType) {
        return switch (eventType) {
            case "refund.succeeded" -> ProviderRefundStatus.SUCCESS;
            case "refund.failed" -> ProviderRefundStatus.FAILED;
            case "refund.processing" -> ProviderRefundStatus.PROCESSING;
            default -> null;
        };
    }
}
