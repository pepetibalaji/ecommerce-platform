package com.ecommerce.order.service;

import com.ecommerce.order.entity.InventoryReleaseReason;
import com.ecommerce.order.entity.OrderStatus;
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
public class PendingPaymentExpiryProcessor {
    private final OrderRepository orderRepository;
    private final OrderRefundRequestService paymentCommands;
    private final Clock clock;
    private final PaymentOutcomeMetrics metrics;
    @Value("${order.payment-expiry.batch-size:25}") private int batchSize;
    @Scheduled(fixedDelayString = "${order.payment-expiry.fixed-delay-ms:60000}")
    @Transactional
    public void expirePendingOrders() {
        for (var order : orderRepository.lockExpiredPending(Instant.now(clock), Math.max(1, batchSize))) {
            paymentCommands.enqueueCancellation(order, null, "ORDER_SYSTEM", "Payment window expired", true);
            log.info("Requested authoritative payment expiry. orderId={}", order.getId());
        }
    }
}
