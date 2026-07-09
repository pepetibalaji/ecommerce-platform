package com.ecommerce.order.kafka;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
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

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        Objects.requireNonNull(event, "OrderCreatedEvent must not be null");
        Objects.requireNonNull(event.getOrderId(), "OrderCreatedEvent.orderId must not be null");

        String topic = KafkaTopics.ORDER_CREATED;
        String key = event.getOrderId().toString();

        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                kafkaTemplate.send(topic, key, event);

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
}