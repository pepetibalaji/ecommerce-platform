package com.ecommerce.order.service;

import com.ecommerce.order.kafka.OrderEventPublisher;
import com.ecommerce.order.repository.OrderRefundRequestOutboxRepository;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Leases and publishes durable payment-refund requests. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderRefundRequestOutboxProcessor {

    private final OrderRefundRequestOutboxRepository repository;
    private final OrderEventPublisher publisher;
    private final PaymentOutcomeMetrics metrics;
    private final Clock clock;

    @Value("${order.refund-request-outbox.batch-size:25}")
    private int batchSize;

    @Value("${order.refund-request-outbox.max-attempts:8}")
    private int maxAttempts;

    @Scheduled(
            fixedDelayString = "${order.refund-request-outbox.fixed-delay-ms:5000}",
            initialDelayString = "${order.refund-request-outbox.initial-delay-ms:1000}"
    )
    @Transactional
    public void publishPending() {
        Instant now = Instant.now(clock);
        for (var request : repository.lockNextPending(now, Math.max(1, batchSize))) {
            try {
                publisher.sendPaymentRefundRequested(request).get();
                request.published(now);
                metrics.refundRequestOutboxPublished();
            } catch (Exception exception) {
                Instant retryAt = now.plusSeconds((long) Math.min(300, Math.pow(2, request.getAttemptCount() + 1)));
                request.failed(exception, retryAt, Math.max(1, maxAttempts));
                if (request.getStatus().name().equals("FAILED")) {
                    metrics.refundRequestOutboxTerminalFailure();
                }
                log.error("Payment refund-request outbox publish failed. outboxId={}, orderId={}, attempt={}",
                        request.getId(), request.getOrderId(), request.getAttemptCount(), exception);
            }
        }
    }
}
