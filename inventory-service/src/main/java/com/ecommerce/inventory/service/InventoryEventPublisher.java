package com.ecommerce.inventory.service;

import com.ecommerce.common.events.inventory.InventoryReleasedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.inventory.entity.Inventory;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InventoryEventPublisher {
    private final InventoryOutboxService outbox;
    void expired(Inventory inventory, int quantity) {
        outbox.enqueue(KafkaTopics.INVENTORY_RELEASED, inventory.getProductId().toString(),
                new InventoryReleasedEvent(null, inventory.getProductId(), quantity, "RESERVATION_EXPIRED", null, null));
    }
    void availability(Inventory inventory) {
        String topic = inventory.getAvailableStock() == 0 ? KafkaTopics.OUT_OF_STOCK : KafkaTopics.LOW_INVENTORY;
        outbox.enqueue(topic, inventory.getProductId().toString(), Map.of("eventId", UUID.randomUUID(),
                "productId", inventory.getProductId(), "sellerUserId", inventory.getSellerId(),
                "availableStock", inventory.getAvailableStock()));
    }
}
