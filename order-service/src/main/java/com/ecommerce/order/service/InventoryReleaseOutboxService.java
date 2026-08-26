package com.ecommerce.order.service;

import com.ecommerce.order.entity.InventoryReleaseOutbox;
import com.ecommerce.order.entity.InventoryReleaseReason;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import com.ecommerce.order.repository.InventoryReleaseOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryReleaseOutboxService {

    private final InventoryReleaseOutboxRepository inventoryReleaseOutboxRepository;
    private final PaymentOutcomeMetrics paymentOutcomeMetrics;

    @Transactional
    public void enqueueFor(Order order, InventoryReleaseReason reason) {
        for (OrderItem item : order.getItems()) {
            UUID reservationId = requireReservationId(order, item);

            if (inventoryReleaseOutboxRepository.existsByReservationId(reservationId)) {
                continue;
            }

            inventoryReleaseOutboxRepository.save(new InventoryReleaseOutbox(
                    order.getId(),
                    item.getId(),
                    reservationId,
                    item.getProductId(),
                    item.getQuantity(),
                    reason
            ));
            paymentOutcomeMetrics.inventoryReleaseQueued(reason.name().toLowerCase());
        }
    }

    private UUID requireReservationId(Order order, OrderItem item) {
        if (order.getId() == null || item.getId() == null || item.getInventoryReservationId() == null) {
            throw new IllegalStateException(
                    "Order " + order.getId() + " contains an item without an inventory reservation id; "
                            + "manual inventory remediation is required before it can be released"
            );
        }

        return item.getInventoryReservationId();
    }
}
