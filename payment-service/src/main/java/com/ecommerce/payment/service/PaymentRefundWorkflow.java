package com.ecommerce.payment.service;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentRefund;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.enums.RefundStatus;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.model.RefundGatewayResponse;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundWorkflow {
    private final PaymentRefundRepository refunds;
    private final PaymentRepository payments;
    private final PaymentEventPublisher publisher;
    private final PaymentMetrics metrics;
    private final MeterRegistry registry;
    @Value("${payment.refunds.lease-seconds:120}") private long leaseSeconds = 120;
    @Value("${payment.refunds.max-attempts:10}") private int maxAttempts = 10;
    @Value("${payment.refunds.retry-base-seconds:30}") private long retryBaseSeconds = 30;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<RefundWork> claim(int batchSize) {
        Instant now = Instant.now();
        return refunds.claimDue(now, Math.min(Math.max(batchSize, 1), 50)).stream().map(refund -> {
            refund.setLeaseToken(UUID.randomUUID());
            refund.setLeaseUntil(now.plusSeconds(Math.max(30, leaseSeconds)));
            refund.setAttemptCount(refund.getAttemptCount() + 1);
            if (refund.getFirstProviderAttemptAt() == null) refund.setFirstProviderAttemptAt(now);
            Payment payment = refund.getPayment();
            return new RefundWork(refund.getId(), payment.getId(), payment.getOrderId(), payment.getProvider(),
                    refund.getAmount(), refund.getCurrency(), refund.getReason(), refund.getProviderPaymentIntentId(),
                    refund.getProviderRefundId(), refund.getProviderIdempotencyKey(), refund.getLeaseToken(),
                    refund.getFirstProviderAttemptAt());
        }).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(RefundWork work, RefundGatewayResponse response) {
        Payment payment = payments.findByOrderIdForUpdate(work.orderId()).orElseThrow();
        PaymentRefund refund = refunds.findByIdForUpdate(work.refundId()).orElseThrow();
        if (!ownsLease(refund, work)) return;
        String status = response == null || response.status() == null ? "" : response.status().toLowerCase(Locale.ROOT);
        if (response != null && hasText(response.providerRefundId())) {
            if (hasText(refund.getProviderRefundId())
                    && !refund.getProviderRefundId().equals(response.providerRefundId())) {
                markManualReview(payment, refund, "PAYMENT_REFUND_PROVIDER_ID_CONFLICT");
                return;
            }
            refund.setProviderRefundId(response.providerRefundId());
        }
        if (response != null && response.success() && Set.of("succeeded", "success", "refunded").contains(status)
                && hasText(refund.getProviderRefundId())) {
            refund.setStatus(RefundStatus.REFUNDED);
            refund.setFailureReason(null);
            refund.setCompletedAt(Instant.now());
            clearLease(refund);
            BigDecimal total = recomputePayment(payment);
            publisher.publishRefundCompleted(payment, refund, total);
            metrics.refundSucceeded(payment.getProvider());
            log.info("refund_completed refundId={} paymentId={} orderId={} attempt={}",
                    refund.getId(), payment.getId(), payment.getOrderId(), refund.getAttemptCount());
        } else if (Set.of("failed", "canceled", "cancelled").contains(status)
                && hasText(refund.getProviderRefundId())) {
            // Only an identified provider refund's terminal status releases its amount reservation.
            refund.setStatus(RefundStatus.REFUND_FAILED);
            refund.setFailureReason("PAYMENT_REFUND_FAILED");
            refund.setCompletedAt(Instant.now());
            clearLease(refund);
            recomputePayment(payment);
            publisher.publishRefundFailed(payment, refund);
            metrics.refundFailed(payment.getProvider());
        } else {
            refund.setStatus(hasText(refund.getProviderRefundId())
                    ? RefundStatus.REFUND_PROCESSING : RefundStatus.REFUND_REQUESTED);
            scheduleOrReview(payment, refund, "PAYMENT_REFUND_IN_PROGRESS");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(RefundWork work) {
        Payment payment = payments.findByOrderIdForUpdate(work.orderId()).orElseThrow();
        PaymentRefund refund = refunds.findByIdForUpdate(work.refundId()).orElseThrow();
        if (ownsLease(refund, work)) scheduleOrReview(payment, refund, "PAYMENT_PROVIDER_UNAVAILABLE");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void manualReview(RefundWork work) {
        Payment payment = payments.findByOrderIdForUpdate(work.orderId()).orElseThrow();
        PaymentRefund refund = refunds.findByIdForUpdate(work.refundId()).orElseThrow();
        if (ownsLease(refund, work)) markManualReview(payment, refund, "PAYMENT_REFUND_ACCEPTANCE_UNKNOWN");
    }

    private boolean ownsLease(PaymentRefund refund, RefundWork work) {
        return Objects.equals(refund.getLeaseToken(), work.leaseToken())
                && Set.of(RefundStatus.REFUND_REQUESTED, RefundStatus.REFUND_PROCESSING).contains(refund.getStatus());
    }

    private void scheduleOrReview(Payment payment, PaymentRefund refund, String code) {
        if (refund.getAttemptCount() >= Math.max(1, maxAttempts)) {
            markManualReview(payment, refund, code);
            return;
        }
        refund.setFailureReason(code);
        long delay = Math.min(3600, Math.max(1, retryBaseSeconds)
                * (1L << Math.min(refund.getAttemptCount() - 1, 10)));
        refund.setNextAttemptAt(Instant.now().plusSeconds(delay));
        clearLease(refund);
        recomputePayment(payment);
        registry.counter("payment.refund.retry.count", "provider", payment.getProvider().name().toLowerCase(Locale.ROOT)).increment();
        log.warn("refund_retry_scheduled refundId={} paymentId={} orderId={} attempt={} nextAttemptAt={} code={}",
                refund.getId(), payment.getId(), payment.getOrderId(), refund.getAttemptCount(), refund.getNextAttemptAt(), code);
    }

    private void markManualReview(Payment payment, PaymentRefund refund, String code) {
        refund.setStatus(RefundStatus.REFUND_MANUAL_REVIEW);
        refund.setFailureReason(code);
        clearLease(refund);
        recomputePayment(payment);
        publisher.publishRefundFailed(payment, refund);
        registry.counter("payment.refund.manual_review.count", "provider", payment.getProvider().name().toLowerCase(Locale.ROOT)).increment();
        log.error("refund_manual_review refundId={} paymentId={} orderId={} attempt={} code={}",
                refund.getId(), payment.getId(), payment.getOrderId(), refund.getAttemptCount(), code);
    }

    private BigDecimal recomputePayment(Payment payment) {
        List<PaymentRefund> all = refunds.findByPayment_IdOrderByCreatedAtDesc(payment.getId());
        BigDecimal total = all.stream().filter(r -> r.getStatus() == RefundStatus.REFUNDED)
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean pending = all.stream().anyMatch(r -> Set.of(RefundStatus.REFUND_REQUESTED,
                RefundStatus.REFUND_PROCESSING).contains(r.getStatus()));
        boolean review = all.stream().anyMatch(r -> r.getStatus() == RefundStatus.REFUND_MANUAL_REVIEW);
        payment.setStatus(total.compareTo(payment.getAmount()) >= 0 ? PaymentStatus.REFUNDED
                : pending ? PaymentStatus.REFUND_PROCESSING : review ? PaymentStatus.REFUND_FAILED : PaymentStatus.SUCCESS);
        payment.setFailureReason(review ? "PAYMENT_REFUND_FAILED" : null);
        return total;
    }

    private void clearLease(PaymentRefund refund) {
        refund.setLeaseToken(null);
        refund.setLeaseUntil(null);
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
