package com.ecommerce.payment.observability;

import com.ecommerce.payment.enums.PaymentProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsTest {

    @Test
    void recordsRequiredPaymentMetricsWithOnlyProviderAsBusinessLabel() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentMetrics metrics = new PaymentMetrics(registry);

        metrics.paymentCreated();
        metrics.checkoutSessionCreated(PaymentProvider.SANDBOX);
        metrics.webhookReceived(PaymentProvider.SANDBOX);
        metrics.webhookDuplicate(PaymentProvider.SANDBOX);
        metrics.paymentSucceeded(PaymentProvider.SANDBOX);

        assertThat(registry.get("payment.created.count").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.checkout_session.created.count")
                .tag("provider", "sandbox").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.webhook.received.count")
                .tag("provider", "sandbox").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.webhook.duplicate.count")
                .tag("provider", "sandbox").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.success.count")
                .tag("provider", "sandbox").counter().count()).isEqualTo(1);
    }
}
