package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderRefundRequestOutbox;
import com.ecommerce.order.repository.OrderRefundRequestOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderRefundRequestService {

    private final OrderRefundRequestOutboxRepository repository;
    private final OrderLifecycleAuditService auditService;
    private final Clock clock;

    /**
     * Creates exactly one full-refund command for an order. Callers must hold the order row lock.
     * Returning an existing row makes repeated cancellation commands safe to retry.
     */
    @Transactional
    public OrderRefundRequestOutbox enqueueFullRefund(
            Order order,
            UUID actorId,
            String actorType,
            String reason
    ) {
        return repository.findByOrderId(order.getId()).orElseGet(() -> {
            if (order.getPaymentId() == null) {
                throw new IllegalStateException("Confirmed order has no payment id: " + order.getId());
            }
            Instant now = Instant.now(clock);
            OrderRefundRequestOutbox outbox = repository.save(new OrderRefundRequestOutbox(
                    order.getId(),
                    order.getPaymentId(),
                    order.getUserId(),
                    actorId,
                    actorType,
                    order.getTotalAmount(),
                    order.getCurrency(),
                    reason,
                    now
            ));
            auditService.record(
                    order.getId(),
                    "REFUND_REQUESTED",
                    actorId,
                    actorType,
                    reason,
                    outbox.getId()
            );
            return outbox;
        });
    }
}
