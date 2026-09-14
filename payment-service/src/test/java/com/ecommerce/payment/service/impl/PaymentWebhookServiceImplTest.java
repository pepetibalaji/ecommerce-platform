package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.webhook.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceImplTest {
    private final PaymentGatewayFactory factory=mock(PaymentGatewayFactory.class);
    private final PaymentGateway gateway=mock(PaymentGateway.class);
    private final VerifiedWebhookInbox inbox=mock(VerifiedWebhookInbox.class);
    private final VerifiedWebhookProcessor processor=mock(VerifiedWebhookProcessor.class);
    private final SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    private final PaymentWebhookServiceImpl service=new PaymentWebhookServiceImpl(factory,mock(PaymentRepository.class),
            mock(PaymentMapper.class),new PaymentMetrics(metrics),inbox,processor);

    @Test void invalidSignatureNeverEntersDurableInboxAndReturnsSafeError() {
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        when(gateway.parseWebhookEvent("raw","invalid")).thenThrow(new IllegalArgumentException("secret provider detail"));
        assertThatThrownBy(()->service.processWebhook(PaymentProvider.STRIPE,"raw","invalid"))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("secret provider detail");
        verifyNoInteractions(inbox,processor);
    }
    @Test void verifiedInboxCommitPrecedesProcessingAndFailureIsRetained() {
        UUID id=UUID.randomUUID();
        var event=ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE).providerEventId("evt_verified")
                .eventType("checkout.session.completed").status(ProviderPaymentStatus.SUCCESS).build();
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        when(gateway.parseWebhookEvent("raw","valid")).thenReturn(event);
        when(inbox.accept(event,"raw")).thenReturn(new VerifiedWebhookInbox.Receipt(id,false));
        RuntimeException failure=new IllegalStateException("storage unavailable");
        when(processor.process(id)).thenThrow(failure);
        assertThat(service.processWebhook(PaymentProvider.STRIPE,"raw","valid").isReceived()).isTrue();
        var order=inOrder(inbox,processor);
        order.verify(inbox).accept(event,"raw"); order.verify(processor).process(id);order.verify(inbox).failed(id,failure);
    }
    @Test void duplicateVerifiedEventsCanRetryUnresolvedEnvelope() {
        UUID id=UUID.randomUUID();
        var event=ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE).providerEventId("evt_retry")
                .eventType("checkout.session.completed").status(ProviderPaymentStatus.SUCCESS).build();
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        when(gateway.parseWebhookEvent("raw","valid")).thenReturn(event);
        when(inbox.accept(event,"raw")).thenReturn(new VerifiedWebhookInbox.Receipt(id,true));
        when(processor.process(id)).thenReturn(WebhookProcessingStatus.PROCESSED);
        assertThat(service.processWebhook(PaymentProvider.STRIPE,"raw","valid").isDuplicate()).isTrue();
        verify(processor).process(id);
    }
}