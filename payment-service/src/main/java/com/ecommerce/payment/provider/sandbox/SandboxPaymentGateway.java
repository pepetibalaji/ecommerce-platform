package com.ecommerce.payment.provider.sandbox;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SandboxPaymentGateway implements PaymentGateway {

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
        if (signature == null || signature.isBlank()) {
            throw new BadRequestException("Sandbox webhook signature is required");
        }
    }

    @Override
    public ProviderWebhookEvent parseWebhookEvent(String payload, String signature) {
        verifyWebhookSignature(payload, signature);

        return ProviderWebhookEvent.builder()
                .provider(PaymentProvider.SANDBOX)
                .providerEventId("sandbox-event-" + System.currentTimeMillis())
                .eventType("sandbox.payment.ignored")
                .status(ProviderPaymentStatus.IGNORED)
                .failureReason("Sandbox webhook parsing is placeholder only")
                .build();
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
}