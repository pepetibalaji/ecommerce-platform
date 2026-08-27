package com.ecommerce.order.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class InventoryReleaseRetryPolicy {

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double jitter;

    public InventoryReleaseRetryPolicy(
            @Value("${order.inventory-release.retry.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${order.inventory-release.retry.max-delay-ms:300000}") long maxDelayMs,
            @Value("${order.inventory-release.retry.jitter:0.20}") double jitter
    ) {
        if (initialDelayMs <= 0 || maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("Inventory release retry delays must be positive and ordered");
        }
        if (jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("Inventory release retry jitter must be between 0 and 1");
        }
        this.initialDelay = Duration.ofMillis(initialDelayMs);
        this.maxDelay = Duration.ofMillis(maxDelayMs);
        this.jitter = jitter;
    }

    public Duration delayForAttempt(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("Attempt count must be at least one");
        }

        long maximumMillis = maxDelay.toMillis();
        long delayMillis = initialDelay.toMillis();
        for (int attempt = 1; attempt < attemptCount && delayMillis < maximumMillis; attempt++) {
            delayMillis = delayMillis > maximumMillis / 2
                    ? maximumMillis
                    : Math.min(maximumMillis, delayMillis * 2);
        }

        double multiplier = jitter == 0
                ? 1
                : 1 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
        return Duration.ofMillis(Math.min(maximumMillis, Math.max(1, Math.round(delayMillis * multiplier))));
    }
}
