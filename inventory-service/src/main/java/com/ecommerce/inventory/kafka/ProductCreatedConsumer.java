package com.ecommerce.inventory.kafka;

import com.ecommerce.common.events.product.ProductCreatedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.inventory.service.InventoryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCreatedConsumer {
    private final InventoryService inventoryService;
    private final MeterRegistry meterRegistry;

    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = "-dlq")
    @KafkaListener(topics = KafkaTopics.PRODUCT_CREATED,
            groupId = "${spring.kafka.consumer.product-created-group:inventory-product-provisioner}")
    public void consume(ProductCreatedEvent event) {
        if (event == null || event.getProductId() == null) {
            throw new IllegalArgumentException("product-created event requires productId");
        }
        inventoryService.createInitialInventory(event.getProductId(), event.getSellerId());
        Counter.builder("inventory_product_created_events_total").register(meterRegistry).increment();
        log.info("Ensured initial inventory exists. productId={}, sellerId={}, eventId={}",
                event.getProductId(), event.getSellerId(), event.getEventId());
    }
}
