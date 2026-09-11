package com.ecommerce.inventory.kafka;

import com.ecommerce.common.events.product.ProductLifecycleEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.inventory.service.ProductLifecycleService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductLifecycleConsumer {
    private final ProductLifecycleService lifecycleService;
    private final MeterRegistry meters;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = "-dlq")
    @KafkaListener(topics = KafkaTopics.PRODUCT_LIFECYCLE,
            groupId = "${spring.kafka.consumer.product-lifecycle-group:inventory-product-lifecycle}")
    public void consume(ProductLifecycleEvent event) {
        boolean applied = lifecycleService.apply(event);
        meters.counter("inventory_product_lifecycle_events_total", "result", applied ? "applied" : "ignored")
                .increment();
        log.info("Processed product lifecycle. productId={}, eventId={}, version={}, applied={}",
                event.productId(), event.eventId(), event.productVersion(), applied);
    }

    @DltHandler
    public void deadLetter(ProductLifecycleEvent event) {
        meters.counter("inventory_product_lifecycle_dead_letters_total").increment();
        log.error("Product lifecycle requires recovery from {}. productId={}, eventId={}",
                KafkaTopics.PRODUCT_LIFECYCLE_DLQ, event == null ? null : event.productId(),
                event == null ? null : event.eventId());
    }
}
