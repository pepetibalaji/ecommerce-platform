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
}
