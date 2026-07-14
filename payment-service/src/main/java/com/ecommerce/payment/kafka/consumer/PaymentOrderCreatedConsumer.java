package com.ecommerce.payment.kafka.consumer;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderCreatedConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "${spring.kafka.consumer.group-id:payment-service}"
    )
    public void onOrderCreated(
            OrderCreatedEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        try {
            putMdc("correlationId", event.getCorrelationId());
            putMdc("traceId", event.getTraceId());
            putMdc("eventId", event.getEventId() == null ? null : event.getEventId().toString());
            putMdc("orderId", event.getOrderId() == null ? null : event.getOrderId().toString());

            log.info(
                    "Received order-created event. key={}, eventId={}, orderId={}, userId={}, amount={}, currency={}, correlationId={}, traceId={}",
                    key,
                    event.getEventId(),
                    event.getOrderId(),
                    event.getUserId(),
                    event.getTotalAmount(),
                    event.getCurrency(),
                    event.getCorrelationId(),
                    event.getTraceId()
            );

            paymentService.preparePaymentFromOrder(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getTotalAmount(),
                    event.getCurrency(),
                    event.getCorrelationId(),
                    event.getTraceId()
            );
        } finally {
            MDC.remove("correlationId");
            MDC.remove("traceId");
            MDC.remove("eventId");
            MDC.remove("orderId");
        }
    }

    private void putMdc(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}