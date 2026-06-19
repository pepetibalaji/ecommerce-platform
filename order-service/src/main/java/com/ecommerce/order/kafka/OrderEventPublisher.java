package com.ecommerce.order.kafka;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(
                KafkaTopics.ORDER_CREATED,
                event.getOrderId().toString(),
                event
        );
    }
}