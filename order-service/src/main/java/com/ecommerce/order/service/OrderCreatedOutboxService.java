package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderCreatedOutbox;
import com.ecommerce.order.repository.OrderCreatedOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderCreatedOutboxService {
    private final OrderCreatedOutboxRepository repository;
    private final Clock clock;
    public void enqueue(Order order) { repository.save(new OrderCreatedOutbox(order.getId(), Instant.now(clock))); }
}
