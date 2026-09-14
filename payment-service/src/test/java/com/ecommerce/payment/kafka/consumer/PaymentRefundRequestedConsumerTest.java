package com.ecommerce.payment.kafka.consumer;

import com.ecommerce.common.events.payment.PaymentRefundRequestedEvent;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.exception.*;
import com.ecommerce.payment.kafka.producer.PaymentRefundRequestOutcomePublisher;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentRefundService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundRequestedConsumerTest {
    PaymentRefundService refunds = mock(PaymentRefundService.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentRefundRequestOutcomePublisher outcomes = mock(PaymentRefundRequestOutcomePublisher.class);
    PaymentRefundRequestedConsumer consumer = new PaymentRefundRequestedConsumer(refunds, payments, outcomes);

    @Test void businessRefusalPersistsSafeRejectionAfterServiceTransactionEnds() {
        PaymentRefundRequestedEvent event = event();
        when(payments.findByIdAndOrderId(event.getPaymentId(), event.getOrderId()))
                .thenReturn(Optional.of(Payment.builder().userId(event.getUserId()).build()));
        when(refunds.refundPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED));
        when(outcomes.publishRejected(event, "PAYMENT_REFUND_NOT_ALLOWED"))
                .thenReturn(CompletableFuture.completedFuture(null));
        consumer.onRefundRequested(event, event.getOrderId().toString());
        verify(outcomes).publishRejected(event, "PAYMENT_REFUND_NOT_ALLOWED");
    }

    @Test void failureToPersistRejectionEscapesForKafkaRedelivery() {
        PaymentRefundRequestedEvent event = event();
        when(payments.findByIdAndOrderId(event.getPaymentId(), event.getOrderId())).thenReturn(Optional.empty());
        when(outcomes.publishRejected(any(), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> consumer.onRefundRequested(event, event.getOrderId().toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void listenersDeserializeTheirCommandsWithoutDependingOnProducerTypeHeaders() throws Exception {
        var methods = List.of(
                PaymentRefundRequestedConsumer.class.getMethod("onRefundRequested", PaymentRefundRequestedEvent.class, String.class),
                PaymentCancellationRequestedConsumer.class.getMethod("onCancellationRequested",
                        com.ecommerce.common.events.payment.PaymentCancellationRequestedEvent.class));
        for (var method : methods) {
            var listener = method.getAnnotation(org.springframework.kafka.annotation.KafkaListener.class);
            Map<String, Object> config = new HashMap<>();
            config.put("spring.json.value.default.type", "com.ecommerce.common.events.order.OrderCreatedEvent");
            for (String setting : listener.properties()) {
                String[] pair = setting.split("=", 2); config.put(pair[0], pair[1]);
            }
            try (var deserializer = new org.springframework.kafka.support.serializer.JsonDeserializer<>()) {
                deserializer.configure(config, false);
                Object result = deserializer.deserialize("commands", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertThat(result).isInstanceOf(method.getParameterTypes()[0]);
            }
        }
    }

    private PaymentRefundRequestedEvent event() {
        return new PaymentRefundRequestedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "CUSTOMER", new BigDecimal("100.00"), "USD",
                "cancel", Instant.now(), "correlation", "trace");
    }
}