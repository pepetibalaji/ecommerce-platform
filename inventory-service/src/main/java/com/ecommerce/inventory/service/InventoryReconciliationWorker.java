package com.ecommerce.inventory.service;

import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Detects low/out stock and invariant drift; DB checks keep invalid counters from persisting. */
@Component
@RequiredArgsConstructor
class InventoryReconciliationWorker {
    private final InventoryRepository inventories;
    private final InventoryEventPublisher publisher;
    private final MeterRegistry meters;
    private final ProductSnapshotGrpcClient snapshots;
    @Value("${inventory.low-stock.default-threshold:5}") private int threshold;
    @Value("${inventory.low-stock.notification-cooldown:PT24H}") private Duration cooldown;
    @Scheduled(fixedDelayString = "${inventory.reconciliation.delay:300000}")
    void reconcile() {
        reconcileProductSnapshot();
        for (Inventory inventory : inventories.findByAvailableStockLessThanEqualAndProductActiveTrue(threshold)) {
            String level = inventory.getAvailableStock() == 0 ? "OUT_OF_STOCK" : "LOW_STOCK";
            if (level.equals(inventory.getLowStockEventLevel()) && inventory.getLowStockEventAt() != null
                    && inventory.getLowStockEventAt().plus(cooldown).isAfter(Instant.now())) continue;
            inventory.setLowStockEventLevel(level);
            inventory.setLowStockEventAt(Instant.now());
            inventories.save(inventory);
            publisher.availability(inventory);
            meters.counter(inventory.getAvailableStock() == 0 ? "inventory_out_of_stock_events_total" : "inventory_low_stock_events_total").increment();
        }
        meters.counter("inventory_reconciliation_runs_total").increment();
    }
    @org.springframework.transaction.annotation.Transactional
    void reconcileProductSnapshot() {
        try {
            HashSet<UUID> seen = new HashSet<>();
            for (ProductSnapshotGrpcClient.Snapshot snapshot : snapshots.fetchAll()) {
                seen.add(snapshot.productId());
                inventories.applyProductSnapshot(UUID.randomUUID(), snapshot.productId(), snapshot.sellerId(),
                        snapshot.active(), snapshot.version(), UUID.randomUUID());
            }
            for (Inventory inventory : inventories.findAll()) {
                if (!seen.contains(inventory.getProductId()) && inventory.isProductActive()) {
                    inventory.setProductActive(false); inventories.save(inventory);
                    meters.counter("inventory_reconciliation_retired_rows_total").increment();
                }
            }
        } catch (RuntimeException failure) {
            meters.counter("inventory_reconciliation_failures_total", "source", "product_snapshot_grpc").increment();
        }
    }
}
