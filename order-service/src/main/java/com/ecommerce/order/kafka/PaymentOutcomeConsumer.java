package com.ecommerce.order.kafka;

import com.ecommerce.common.events.payment.PaymentFailedEvent;
import com.ecommerce.common.events.payment.PaymentSuccessEvent;
import com.ecommerce.common.events.payment.PaymentRefundCompletedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
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
public class PaymentOutcomeConsumer {

    private final OrderService orderService;
    private final PaymentOutcomeMetrics paymentOutcomeMetrics;

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS,
            groupId = "${order.payment-outcome-consumer-group:order-service-payment-outcomes}")
    public void onPaymentSuccess(
            PaymentSuccessEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset
    ) {
        populateMdc(event.getCorrelationId(), event.getTraceId(), event.getEventId(),
                event.getOrderId(), event.getPaymentId());
        try {
            log.info("Received payment-success event. topic={}, partition={}, offset={}, key={}, eventId={}, orderId={}, paymentId={}, correlationId={}, traceId={}",
                    topic, partition, offset, key, event.getEventId(), event.getOrderId(), event.getPaymentId(),
                    event.getCorrelationId(), event.getTraceId());
            paymentOutcomeMetrics.consumed("success");
            orderService.handlePaymentSuccess(event);
        } finally {
            clearMdc();
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "${order.payment-outcome-consumer-group:order-service-payment-outcomes}")
    public void onPaymentFailure(
            PaymentFailedEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset
    ) {
        populateMdc(event.getCorrelationId(), event.getTraceId(), event.getEventId(),
                event.getOrderId(), event.getPaymentId());
        try {
            log.info("Received payment-failed event. topic={}, partition={}, offset={}, key={}, eventId={}, orderId={}, paymentId={}, correlationId={}, traceId={}",
                    topic, partition, offset, key, event.getEventId(), event.getOrderId(), event.getPaymentId(),
                    event.getCorrelationId(), event.getTraceId());
            paymentOutcomeMetrics.consumed("failure");
            orderService.handlePaymentFailure(event);
        } finally {
            clearMdc();
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_REFUND_COMPLETED,
            groupId = "${order.payment-outcome-consumer-group:order-service-payment-outcomes}")
    public void onRefundCompleted(PaymentRefundCompletedEvent event) {
        populateMdc(event.getCorrelationId(), event.getTraceId(), event.getEventId(), event.getOrderId(), event.getPaymentId());
        try {
            log.info("Received payment-refund-completed event. eventId={}, orderId={}, refundId={}, fullRefund={}",
                    event.getEventId(), event.getOrderId(), event.getRefundId(), event.isFullRefund());
            orderService.handleRefundCompleted(event);
        } finally { clearMdc(); }
    }

    private void populateMdc(
            String correlationId,
            String traceId,
            java.util.UUID eventId,
            java.util.UUID orderId,
            java.util.UUID paymentId
    ) {
        putMdc("correlationId", correlationId);
        putMdc("traceId", traceId);
        putMdc("eventId", eventId == null ? null : eventId.toString());
        putMdc("orderId", orderId == null ? null : orderId.toString());
        putMdc("paymentId", paymentId == null ? null : paymentId.toString());
    }

    private void putMdc(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private void clearMdc() {
        MDC.remove("correlationId");
        MDC.remove("traceId");
        MDC.remove("eventId");
        MDC.remove("orderId");
        MDC.remove("paymentId");
    }
}
