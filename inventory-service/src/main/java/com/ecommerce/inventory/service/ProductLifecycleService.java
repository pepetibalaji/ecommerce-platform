package com.ecommerce.inventory.service;

import com.ecommerce.common.events.product.ProductLifecycleEvent;
import com.ecommerce.inventory.repository.InventoryRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductLifecycleService {
    private static final Set<String> SUPPORTED_TYPES = Set.of("product.created", "product.updated",
            "product.deactivated", "product.reactivated", "product.archived", "product.reconciled");
    private final InventoryRepository inventoryRepository;

    /** Snapshot upserts recover missed creates and make duplicate/reordered delivery harmless. */
    @Transactional
    public boolean apply(ProductLifecycleEvent event) {
        if (event == null || event.eventId() == null || event.productId() == null
                || event.occurredAt() == null || event.productVersion() < 1
                || event.schemaVersion() != 1 || event.eventType() == null
                || !SUPPORTED_TYPES.contains(event.eventType())) {
            throw new IllegalArgumentException("Invalid or unsupported product lifecycle event");
        }
        if ((Set.of("product.deactivated", "product.archived").contains(event.eventType()) && event.active())
                || ("product.reactivated".equals(event.eventType()) && !event.active())) {
            throw new IllegalArgumentException("Product lifecycle state does not match event type");
        }
        return inventoryRepository.applyProductSnapshot(UUID.randomUUID(), event.productId(), event.sellerId(),
                event.active(), event.productVersion(), event.eventId()) == 1;
    }
}
