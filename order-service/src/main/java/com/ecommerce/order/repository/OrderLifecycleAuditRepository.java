package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderLifecycleAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderLifecycleAuditRepository extends JpaRepository<OrderLifecycleAudit, UUID> {
    List<OrderLifecycleAudit> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
