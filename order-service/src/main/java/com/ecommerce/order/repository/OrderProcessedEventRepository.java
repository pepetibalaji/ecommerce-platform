package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderProcessedEventRepository extends JpaRepository<OrderProcessedEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}
