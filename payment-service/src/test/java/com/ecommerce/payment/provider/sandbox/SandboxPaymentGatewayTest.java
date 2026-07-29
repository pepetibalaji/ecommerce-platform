package com.ecommerce.payment.provider.sandbox;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.dto.response.ProviderRefundStatus;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxPaymentGatewayTest {

    @Test
    void createsDeterministicLocalCheckoutSession() {
        SandboxPaymentGateway gateway = new SandboxPaymentGateway(new PaymentProviderProperties());
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        CheckoutSessionResult result = gateway.createCheckoutSession(CreateCheckoutSessionCommand.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .userId(UUID.randomUUID())
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .idempotencyKey("sandbox-1")
                .build());

        assertThat(result.getProvider()).isEqualTo(PaymentProvider.SANDBOX);
        assertThat(result.getProviderSessionId()).isEqualTo("sandbox-session-" + paymentId);
        assertThat(result.getProviderPaymentIntentId()).isEqualTo("sandbox-intent-" + paymentId);
        assertThat(result.getCheckoutUrl()).contains("paymentId=" + paymentId).contains("orderId=" + orderId);
    }

    @Test
    void rejectsMissingSandboxWebhookSignature() {
        SandboxPaymentGateway gateway = new SandboxPaymentGateway(new PaymentProviderProperties());

        assertThatThrownBy(() -> gateway.verifyWebhookSignature("payload", " "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid sandbox webhook signature");
    }

    @Test
    void mapsDeterministicPaymentAndRefundWebhookEvents() {
        SandboxPaymentGateway gateway = new SandboxPaymentGateway(new PaymentProviderProperties());
        var payment = gateway.parseWebhookEvent("""
                {"eventId":"event-success","eventType":"payment.succeeded","providerPaymentIntentId":"intent-1"}
                """, "sandbox-test-signature");
        var refund = gateway.parseWebhookEvent("""
                {"eventId":"event-refund","eventType":"refund.succeeded","providerRefundId":"refund-1"}
                """, "sandbox-test-signature");

        assertThat(payment.getStatus()).isEqualTo(com.ecommerce.payment.provider.model.ProviderPaymentStatus.SUCCESS);
        assertThat(refund.getRefundStatus()).isEqualTo(ProviderRefundStatus.SUCCESS);
    }
}
