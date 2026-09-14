package com.ecommerce.payment.kafka.consumer;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentOrderCreatedConsumerTest {

    @Test
    void preparesPaymentUsingOrderEventValuesAndClearsCorrelationMdc() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentOrderCreatedConsumer consumer = new PaymentOrderCreatedConsumer(paymentService);
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, userId, new BigDecimal("42.00"), "usd", List.of(), "corr-1", "trace-1"
        );

        consumer.onOrderCreated(event, orderId.toString());

        verify(paymentService).preparePaymentFromOrder(
                orderId, userId, new BigDecimal("42.00"), "USD", "corr-1", "trace-1"
        );
        org.assertj.core.api.Assertions.assertThat(MDC.get("correlationId")).isNull();
        org.assertj.core.api.Assertions.assertThat(MDC.get("traceId")).isNull();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"schema", "source", "type", "event-id", "order-id",
            "user-id", "timestamp", "future-timestamp", "amount", "zero-amount", "currency", "key"})
    void malformedEnvelopeNeverReachesPreparation(String invalid) {
        PaymentService service = mock(PaymentService.class);
        var consumer = new PaymentOrderCreatedConsumer(service);
        var event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("42.00"),
                "USD", List.of(), "corr", "trace");
        String key = event.getOrderId().toString();
        switch (invalid) {
            case "schema" -> event.setSchemaVersion("2.0");
            case "source" -> event.setSource("untrusted-service");
            case "type" -> event.setEventType("PAYMENT_SUCCESS");
            case "event-id" -> event.setEventId(null);
            case "order-id" -> event.setOrderId(null);
            case "user-id" -> event.setUserId(null);
            case "timestamp" -> event.setOccurredAt(null);
            case "future-timestamp" -> event.setOccurredAt(java.time.Instant.now().plusSeconds(120));
            case "amount" -> event.setTotalAmount(null);
            case "zero-amount" -> event.setTotalAmount(BigDecimal.ZERO);
            case "currency" -> event.setCurrency("USDX");
            case "key" -> key = UUID.randomUUID().toString();
            default -> throw new AssertionError(invalid);
        }
        String receivedKey = key;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.onOrderCreated(event, receivedKey))
                .isInstanceOf(com.ecommerce.common.exception.BadRequestException.class);
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test void rejectedPreparationPropagatesForKafkaRetryAndClearsMdc() {
        var service = mock(PaymentService.class);
        var consumer = new PaymentOrderCreatedConsumer(service);
        var event = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("42.00"),
                "USD", List.of(), "corr", "trace");
        var failure = new IllegalStateException("trusted lookup unavailable");
        org.mockito.Mockito.doThrow(failure).when(service).preparePaymentFromOrder(event.getOrderId(), event.getUserId(),
                event.getTotalAmount(), event.getCurrency(), event.getCorrelationId(), event.getTraceId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.onOrderCreated(event, event.getOrderId().toString()))
                .isSameAs(failure);
        for (String field : new String[]{"correlationId", "traceId", "eventId", "orderId"})
            org.assertj.core.api.Assertions.assertThat(MDC.get(field)).isNull();
    }

    @Test void nullEventIsRejectedWithoutPreparation() {
        var service = mock(PaymentService.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PaymentOrderCreatedConsumer(service).onOrderCreated(null, null))
                .isInstanceOf(com.ecommerce.common.exception.BadRequestException.class);
        org.mockito.Mockito.verifyNoInteractions(service);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"eventId", "schemaVersion", "occurredAt", "source", "eventType"})
    void missingJsonEnvelopeMetadataCannotInheritTrustedConstructorDefaults(String missingField) throws Exception {
        var service = mock(PaymentService.class);
        var original = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("42.00"),
                "USD", List.of(), "corr", "trace");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(original);
        json.remove(missingField);
        var deserialized = mapper.treeToValue(json, OrderCreatedEvent.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new PaymentOrderCreatedConsumer(service).onOrderCreated(deserialized, original.getOrderId().toString()))
                .isInstanceOf(com.ecommerce.common.exception.BadRequestException.class);
        org.mockito.Mockito.verifyNoInteractions(service);
    }

}