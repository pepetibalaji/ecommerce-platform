package com.ecommerce.order.kafka;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.order.OrderCompletedEvent;
import com.ecommerce.common.events.payment.PaymentRefundRequestedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.order.entity.OrderRefundRequestOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        Objects.requireNonNull(event, "OrderCreatedEvent must not be null");
        Objects.requireNonNull(event.getOrderId(), "OrderCreatedEvent.orderId must not be null");

        String topic = KafkaTopics.ORDER_CREATED;
        String key = event.getOrderId().toString();

        CompletableFuture<SendResult<String, Object>> future = sendOrderCreated(event);

        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error(
                        "Failed to publish Kafka event. topic={}, key={}, eventId={}, eventType={}, orderId={}, userId={}, totalAmount={}, currency={}, correlationId={}, traceId={}",
                        topic,
                        key,
                        event.getEventId(),
                        event.getEventType(),
                        event.getOrderId(),
                        event.getUserId(),
                        event.getTotalAmount(),
                        event.getCurrency(),
                        event.getCorrelationId(),
                        event.getTraceId(),
                        exception
                );
                return;
            }

            log.info(
                    "Published Kafka event. topic={}, partition={}, offset={}, key={}, eventId={}, eventType={}, orderId={}, userId={}, totalAmount={}, currency={}, correlationId={}, traceId={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    key,
                    event.getEventId(),
                    event.getEventType(),
                    event.getOrderId(),
                    event.getUserId(),
                    event.getTotalAmount(),
                    event.getCurrency(),
                    event.getCorrelationId(),
                    event.getTraceId()
            );
        });
    }

    public CompletableFuture<SendResult<String, Object>> sendOrderCreated(com.ecommerce.order.entity.Order order) {
        java.util.List<com.ecommerce.common.events.order.OrderItemEvent> items = order.getItems().stream()
                .map(item -> new com.ecommerce.common.events.order.OrderItemEvent(item.getProductId(), item.getQuantity(), item.getPrice(), item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))).toList();
        return sendOrderCreated(new OrderCreatedEvent(order.getId(), order.getUserId(), order.getTotalAmount(), order.getCurrency(), items, order.getId().toString(), null));
    }

    public CompletableFuture<SendResult<String, Object>> sendOrderCreated(OrderCreatedEvent event) {
        Objects.requireNonNull(event, "OrderCreatedEvent must not be null");
        Objects.requireNonNull(event.getOrderId(), "OrderCreatedEvent.orderId must not be null");
        return kafkaTemplate.send(KafkaTopics.ORDER_CREATED, event.getOrderId().toString(), event);
    }

    /**
     * Payment Service consumes this command asynchronously. A successful Kafka send only proves
     * delivery to Kafka; the order remains REFUND_REQUESTED until its outcome event is consumed.
     */
    public CompletableFuture<SendResult<String, Object>> sendPaymentRefundRequested(
            OrderRefundRequestOutbox request
    ) {
        Objects.requireNonNull(request, "Refund request outbox row must not be null");
        if (!"REFUND".equals(request.getCommandType())) {
            var event = new com.ecommerce.common.events.payment.PaymentCancellationRequestedEvent(
                    request.getId(), request.getOrderId(), request.getUserId(), request.getRequestedBy(),
                    request.getActorType(), request.getAmount(), request.getCurrency(), request.getReason(),
                    request.getCreatedAt(), request.getCorrelationId(), request.getTraceId());
            event.setExpiryRequested("EXPIRY".equals(request.getCommandType()));
            return kafkaTemplate.send(KafkaTopics.PAYMENT_CANCELLATION_REQUESTED, request.getOrderId().toString(), event);
        }
        PaymentRefundRequestedEvent event = new PaymentRefundRequestedEvent(
                request.getId(),
                request.getPaymentId(),
                request.getOrderId(),
                request.getUserId(),
                request.getRequestedBy(),
                request.getActorType(),
                request.getAmount(),
                request.getCurrency(),
                request.getReason(),
                request.getCreatedAt(),
                request.getCorrelationId(),
                request.getTraceId()
        );
        return kafkaTemplate.send(KafkaTopics.PAYMENT_REFUND_REQUESTED, request.getOrderId().toString(), event);
    }

    public void publishOrderCompleted(OrderCompletedEvent event) {
        kafkaTemplate.send(KafkaTopics.ORDER_COMPLETED, event.getOrderId().toString(), event);
    }
}
