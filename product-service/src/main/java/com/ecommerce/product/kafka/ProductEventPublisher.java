package com.ecommerce.product.kafka;

import com.ecommerce.common.events.product.ProductCreatedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.product.entity.Product;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Counter publishFailures;

    public ProductEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishFailures = Counter.builder("product_created_event_publish_failures_total")
                .description("Product-created events that could not be published").register(meterRegistry);
    }

    public void publishProductCreated(Product product) {
        ProductCreatedEvent event = new ProductCreatedEvent(product.getId(), product.getSellerId(), product.getId().toString());
        kafkaTemplate.send(KafkaTopics.PRODUCT_CREATED, product.getId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        publishFailures.increment();
                        log.error("Product inventory provisioning event failed; reconcile productId={}", product.getId(), error);
                    }
                });
    }
}
