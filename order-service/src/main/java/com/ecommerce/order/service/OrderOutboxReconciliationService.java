package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderOutboxReconciliationResponse;
import com.ecommerce.order.dto.OutboxStatusCounts;
import com.ecommerce.order.entity.InventoryReleaseStatus;
import com.ecommerce.order.entity.RefundRequestStatus;
import com.ecommerce.order.repository.CheckoutCompensationOutboxRepository;
import com.ecommerce.order.repository.InventoryReleaseOutboxRepository;
import com.ecommerce.order.repository.OrderCreatedOutboxRepository;
import com.ecommerce.order.repository.OrderRefundRequestOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Read-only operations view of all durable order-side hand-offs. */
@Service
@RequiredArgsConstructor
public class OrderOutboxReconciliationService {

    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;
    private final InventoryReleaseOutboxRepository inventoryReleaseOutboxRepository;
    private final CheckoutCompensationOutboxRepository checkoutCompensationOutboxRepository;
    private final OrderRefundRequestOutboxRepository refundRequestOutboxRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OrderOutboxReconciliationResponse snapshot() {
        return new OrderOutboxReconciliationResponse(
                Instant.now(clock),
                new OutboxStatusCounts(
                        orderCreatedOutboxRepository.countByStatus("PENDING"),
                        orderCreatedOutboxRepository.countByStatus("PUBLISHED"),
                        0,
                        orderCreatedOutboxRepository.countByStatus("FAILED"),
                        0
                ),
                new OutboxStatusCounts(
                        inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.PENDING),
                        0,
                        inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.COMPLETED),
                        inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.FAILED),
                        inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.MANUAL_REVIEW)
                ),
                new OutboxStatusCounts(
                        checkoutCompensationOutboxRepository.countByStatus("PENDING"),
                        0,
                        checkoutCompensationOutboxRepository.countByStatus("COMPLETED"),
                        checkoutCompensationOutboxRepository.countByStatus("FAILED"),
                        0
                ),
                new OutboxStatusCounts(
                        refundRequestOutboxRepository.countByStatus(RefundRequestStatus.PENDING),
                        refundRequestOutboxRepository.countByStatus(RefundRequestStatus.PUBLISHED),
                        0,
                        refundRequestOutboxRepository.countByStatus(RefundRequestStatus.FAILED),
                        0
                )
        );
    }
}
