package com.ecommerce.order.service;

import com.ecommerce.order.entity.OrderCreatedOutbox;
import com.ecommerce.order.kafka.OrderEventPublisher;
import com.ecommerce.order.repository.OrderCreatedOutboxRepository;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;

@Component @RequiredArgsConstructor @Slf4j
public class OrderCreatedOutboxProcessor {
    private final OrderCreatedOutboxRepository outboxRepository;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher publisher;
    private final Clock clock;
    private final PaymentOutcomeMetrics metrics;
    @Value("${order.order-created-outbox.batch-size:25}") private int batchSize;
    @Value("${order.order-created-outbox.max-attempts:8}") private int maxAttempts;
    @Scheduled(fixedDelayString = "${order.order-created-outbox.fixed-delay-ms:5000}", initialDelayString = "${order.order-created-outbox.initial-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        Instant now = Instant.now(clock);
        for (OrderCreatedOutbox entry : outboxRepository.lockNextPending(Math.max(1, batchSize), now)) {
            try {
                var order = orderRepository.findById(entry.getOrderId()).orElseThrow();
                publisher.sendOrderCreated(order).get();
                entry.published(now);
                metrics.orderCreatedOutboxPublished();
            } catch (Exception exception) {
                entry.failed(exception.getMessage(), now.plusSeconds((long) Math.min(300, Math.pow(2, entry.getAttemptCount() + 1))), Math.max(1, maxAttempts));
                if ("FAILED".equals(entry.getStatus())) metrics.orderCreatedOutboxTerminalFailure();
                log.error("order-created outbox publish failed. outboxId={}, orderId={}, attempt={}", entry.getId(), entry.getOrderId(), entry.getAttemptCount(), exception);
            }
        }
    }
}
