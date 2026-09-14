package com.ecommerce.order.service;

import com.ecommerce.order.entity.OrderLifecycleAudit;
import com.ecommerce.order.repository.OrderLifecycleAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderLifecycleAuditService {

    private final OrderLifecycleAuditRepository repository;
    private final Clock clock;

    public void record(
            UUID orderId,
            String action,
            UUID actorId,
            String actorType,
            String reason,
            UUID refundRequestId
    ) {
        repository.save(new OrderLifecycleAudit(
                orderId,
                action,
                actorId,
                actorType,
                normalizeReason(reason),
                refundRequestId,
                Instant.now(clock)
        ));
    }

    public List<OrderLifecycleAudit> findForOrder(UUID orderId) {
        return repository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        return normalized.length() <= 1_000 ? normalized : normalized.substring(0, 1_000);
    }
}
