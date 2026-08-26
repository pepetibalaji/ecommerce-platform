package com.ecommerce.order.service;

import com.ecommerce.order.entity.InventoryReleaseOutbox;
import com.ecommerce.order.grpc.InventoryGrpcClient;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import com.ecommerce.order.repository.InventoryReleaseOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReleaseOutboxProcessor {

    private final InventoryReleaseOutboxRepository inventoryReleaseOutboxRepository;
    private final InventoryGrpcClient inventoryGrpcClient;
    private final PaymentOutcomeMetrics paymentOutcomeMetrics;

    @Value("${order.inventory-release.batch-size:25}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${order.inventory-release.fixed-delay-ms:5000}",
            initialDelayString = "${order.inventory-release.initial-delay-ms:1000}"
    )
    @Transactional
    public void processPendingReleases() {
        List<InventoryReleaseOutbox> commands = inventoryReleaseOutboxRepository
                .lockNextPending(Math.max(1, batchSize));

        for (InventoryReleaseOutbox command : commands) {
            try {
                inventoryGrpcClient.releaseStock(
                        command.getProductId(),
                        command.getQuantity(),
                        command.getReservationId()
                );
                command.markCompleted();
                paymentOutcomeMetrics.inventoryReleaseSucceeded(command.getReason().name().toLowerCase());
                log.info("Completed inventory-release command. commandId={}, orderId={}, reservationId={}, reason={}",
                        command.getId(), command.getOrderId(), command.getReservationId(), command.getReason());
            } catch (RuntimeException exception) {
                command.recordFailure(exception.getMessage());
                paymentOutcomeMetrics.inventoryReleaseFailed(command.getReason().name().toLowerCase());
                log.warn("Inventory-release command will be retried. commandId={}, orderId={}, reservationId={}, reason={}, attempt={}",
                        command.getId(), command.getOrderId(), command.getReservationId(), command.getReason(),
                        command.getAttemptCount(), exception);
            }
        }
    }
}
