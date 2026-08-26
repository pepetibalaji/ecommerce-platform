package com.ecommerce.order.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentOutcomeMetricsTest {

    @Test
    void recordsPaymentOutcomeMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentOutcomeMetrics metrics = new PaymentOutcomeMetrics(registry);

        metrics.consumed("success");
        metrics.orderUpdated("success");
        metrics.duplicateIgnored();
        metrics.lateEventIgnored("failure");
        metrics.retry();
        metrics.deadLettered();
        metrics.inventoryReleaseQueued("payment_failed");
        metrics.inventoryReleaseSucceeded("payment_failed");
        metrics.inventoryReleaseFailed("payment_failed");

        assertThat(registry.get("order.payment_outcome.consumed.count")
                .tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("order.payment_outcome.dlq.count").counter().count()).isEqualTo(1);
        assertThat(registry.get("order.inventory_release.completed.count")
                .tag("reason", "payment_failed").counter().count()).isEqualTo(1);
    }
}
