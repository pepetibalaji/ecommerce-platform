package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.dto.response.WebhookAckResponse;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.repository.PaymentWebhookEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceImplTest {

    @Test
    void ignoresDuplicateWebhookWithoutPublishingAnotherPaymentEvent() {
        PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentWebhookEventRepository webhookEvents = mock(PaymentWebhookEventRepository.class);
        PaymentEventPublisher eventPublisher = mock(PaymentEventPublisher.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentWebhookServiceImpl service = service(gatewayFactory, webhookEvents, eventPublisher,
                new PaymentMetrics(registry));
        ProviderWebhookEvent providerEvent = ProviderWebhookEvent.builder()
                .provider(PaymentProvider.SANDBOX)
                .providerEventId("event-duplicate")
                .eventType("sandbox.payment.ignored")
                .status(ProviderPaymentStatus.IGNORED)
                .build();
        when(gatewayFactory.getGateway(PaymentProvider.SANDBOX)).thenReturn(gateway);
        when(gateway.parseWebhookEvent("payload", "signature")).thenReturn(providerEvent);
        when(webhookEvents.insertReceivedEventIfAbsent(
                any(), eq("SANDBOX"), eq("event-duplicate"), isNull(),
                eq("sandbox.payment.ignored"), eq("RECEIVED"), isNull(), any(), isNull()))
                .thenReturn(0);

        WebhookAckResponse response = service.processWebhook(PaymentProvider.SANDBOX, "payload", "signature");

        assertThat(response.isReceived()).isTrue();
        assertThat(response.isDuplicate()).isTrue();
        assertThat(response.getProcessingStatus()).isEqualTo(WebhookProcessingStatus.IGNORED);
        assertThat(registry.get("payment.webhook.received.count").tag("provider", "sandbox").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("payment.webhook.duplicate.count").tag("provider", "sandbox").counter().count())
                .isEqualTo(1);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void recordsInvalidWebhookSignatureAndRethrowsProviderError() {
        PaymentGatewayFactory gatewayFactory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentWebhookServiceImpl service = service(gatewayFactory,
                mock(PaymentWebhookEventRepository.class), mock(PaymentEventPublisher.class),
                new PaymentMetrics(registry));
        when(gatewayFactory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        when(gateway.parseWebhookEvent("payload", "bad-signature"))
                .thenThrow(new BadRequestException("Invalid Stripe signature"));

        assertThatThrownBy(() -> service.processWebhook(PaymentProvider.STRIPE, "payload", "bad-signature"))
                .isInstanceOf(BadRequestException.class);
        assertThat(registry.get("payment.webhook.invalid_signature.count")
                .tag("provider", "stripe").counter().count()).isEqualTo(1);
    }

    private PaymentWebhookServiceImpl service(
            PaymentGatewayFactory gatewayFactory,
            PaymentWebhookEventRepository webhookEvents,
            PaymentEventPublisher eventPublisher,
            PaymentMetrics metrics
    ) {
        return new PaymentWebhookServiceImpl(
                gatewayFactory,
                mock(PaymentRepository.class),
                mock(PaymentAttemptRepository.class),
                mock(PaymentRefundRepository.class),
                webhookEvents,
                eventPublisher,
                metrics
        );
    }
}
