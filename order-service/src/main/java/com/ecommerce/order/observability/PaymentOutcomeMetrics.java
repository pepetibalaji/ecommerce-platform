package com.ecommerce.order.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutcomeMetrics {

    private final MeterRegistry meterRegistry;

    public PaymentOutcomeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void consumed(String outcome) {
        increment("order.payment_outcome.consumed.count", "outcome", outcome);
    }

    public void orderUpdated(String outcome) {
        increment("order.payment_outcome.order_updated.count", "outcome", outcome);
    }

    public void duplicateIgnored() {
        increment("order.payment_outcome.duplicate_ignored.count");
    }

    public void lateEventIgnored(String outcome) {
        increment("order.payment_outcome.late_ignored.count", "outcome", outcome);
    }

    public void retry() {
        increment("order.payment_outcome.retry.count");
    }

    public void deadLettered() {
        increment("order.payment_outcome.dlq.count");
    }

    public void inventoryReleaseQueued(String reason) {
        increment("order.inventory_release.queued.count", "reason", reason);
    }

    public void inventoryReleaseSucceeded(String reason) {
        increment("order.inventory_release.completed.count", "reason", reason);
    }

    public void inventoryReleaseFailed(String reason) {
        increment("order.inventory_release.failed.count", "reason", reason);
    }

    private void increment(String name, String... tags) {
        Counter.builder(name).tags(tags).register(meterRegistry).increment();
    }
}
