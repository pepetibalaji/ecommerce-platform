package com.ecommerce.payment.observability;

import com.ecommerce.payment.enums.PaymentProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * Owns the low-cardinality business metrics emitted by the payment service.
 * Provider is the only business dimension so that payment IDs, order IDs and
 * webhook payload data never become Prometheus labels.
 */
@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void paymentCreated() {
        increment("payment.created.count");
    }

    public void checkoutSessionCreated(PaymentProvider provider) {
        increment("payment.checkout_session.created.count", provider);
    }

    public void paymentSucceeded(PaymentProvider provider) {
        increment("payment.success.count", provider);
    }

    public void paymentFailed(PaymentProvider provider) {
        increment("payment.failed.count", provider);
    }

    public void paymentCancelled(PaymentProvider provider) {
        increment("payment.cancelled.count", provider);
    }

    public void refundRequested(PaymentProvider provider) {
        increment("payment.refund.requested.count", provider);
    }

    public void refundSucceeded(PaymentProvider provider) {
        increment("payment.refund.success.count", provider);
    }

    public void refundFailed(PaymentProvider provider) {
        increment("payment.refund.failed.count", provider);
    }

    public void webhookReceived(PaymentProvider provider) {
        increment("payment.webhook.received.count", provider);
    }

    public void webhookDuplicate(PaymentProvider provider) {
        increment("payment.webhook.duplicate.count", provider);
    }

    public void webhookInvalidSignature(PaymentProvider provider) {
        increment("payment.webhook.invalid_signature.count", provider);
    }

    public <T> T recordProviderLatency(PaymentProvider provider, Callable<T> operation) {
        Timer timer = Timer.builder("payment.provider.latency")
                .tag("provider", provider.name().toLowerCase())
                .register(meterRegistry);
        try {
            return timer.recordCallable(operation);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Payment provider operation failed", exception);
        }
    }

    private void increment(String name) {
        Counter.builder(name).register(meterRegistry).increment();
    }

    private void increment(String name, PaymentProvider provider) {
        Counter.builder(name)
                .tag("provider", provider.name().toLowerCase())
                .register(meterRegistry)
                .increment();
    }
}
