package com.ecommerce.payment.service;

import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.RefundGatewayRequest;
import com.ecommerce.payment.provider.model.RefundGatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundWorker {
    private final PaymentRefundWorkflow workflow;
    private final PaymentGatewayFactory gateways;
    @Value("${payment.refunds.batch-size:20}") private int batchSize = 20;

    @Scheduled(fixedDelayString = "${payment.refunds.poll-delay-ms:5000}")
    public void run() {
        // Claim individually: a slow provider cannot age every lease in a claimed batch.
        for (int index = 0; index < Math.min(Math.max(batchSize, 1), 50); index++) {
            var claimed = workflow.claim(1);
            if (claimed.isEmpty()) return;
            RefundWork work = claimed.getFirst();
            try {
                if ((work.providerRefundId() == null || work.providerRefundId().isBlank())
                        && (work.providerIdempotencyKey() == null || work.providerPaymentIntentId() == null
                        || work.firstProviderAttemptAt().isBefore(Instant.now().minus(23, ChronoUnit.HOURS)))) {
                    // Never replay an unknown accepted request after Stripe's 24-hour key retention window.
                    workflow.manualReview(work);
                    continue;
                }
                PaymentGateway gateway = gateways.getGateway(work.provider());
                RefundGatewayResponse response = work.providerRefundId() != null && !work.providerRefundId().isBlank()
                        ? gateway.retrieveRefund(work.providerRefundId())
                        : gateway.refund(new RefundGatewayRequest(work.paymentId(), work.orderId(),
                                work.providerPaymentIntentId(), work.amount(), work.currency(), work.reason(),
                                work.providerIdempotencyKey()));
                workflow.complete(work, response);
            } catch (RuntimeException failure) {
                log.warn("refund_provider_attempt_uncertain refundId={} paymentId={} orderId={} exceptionType={}",
                        work.refundId(), work.paymentId(), work.orderId(), failure.getClass().getSimpleName());
                // Retains the original key after a timeout, including timeout after provider acceptance.
                workflow.retry(work);
            }
        }
    }
}
