package com.ecommerce.payment.outbox;

import com.ecommerce.payment.entity.PaymentRefund;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentRefundRepository refunds;
    private final PaymentEventPublisher publisher;
    private final MeterRegistry metrics;
    private final java.util.concurrent.atomic.AtomicLong deadCount = new java.util.concurrent.atomic.AtomicLong();

    @jakarta.annotation.PostConstruct
    void registerMetrics() { metrics.gauge("payment.outbox.dead.count",deadCount); }

    @Scheduled(fixedDelayString = "${payment.expiry.poll-delay-ms:15000}")
    public void expire() {
        var orders = jdbc.query("""
                SELECT p.order_id FROM payments p WHERE p.status IN ('PENDING','REQUIRES_CUSTOMER_ACTION')
                AND EXISTS (SELECT 1 FROM payment_attempts a WHERE a.payment_id=p.id
                  AND a.status IN ('CREATED','REQUIRES_CUSTOMER_ACTION') AND a.expires_at<=now())
                ORDER BY p.created_at LIMIT 100
                """, (rs,n)->rs.getObject(1,UUID.class));
        for (UUID orderId:orders) transactions.executeWithoutResult(tx -> {
            // All webhook/checkout/refund/expiry writers take the same payment lock first.
            var payment = payments.findByOrderIdForUpdate(orderId).orElseThrow();
            if (payment.getStatus()!=PaymentStatus.PENDING && payment.getStatus()!=PaymentStatus.REQUIRES_CUSTOMER_ACTION) return;
            var attempt = attempts.findTopByPayment_IdOrderByCreatedAtDesc(payment.getId()).orElse(null);
            if (attempt==null || attempt.getExpiresAt()==null || attempt.getExpiresAt().isAfter(Instant.now())
                    || (attempt.getStatus()!=PaymentAttemptStatus.CREATED && attempt.getStatus()!=PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION)) return;
            attempt.setStatus(PaymentAttemptStatus.EXPIRED);
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setFailureReason("PAYMENT_EXPIRED");
            attempts.save(attempt); payments.save(payment);
            publisher.publishPaymentExpired(payment);
            metrics.counter("payment.expired", "provider",payment.getProvider().name()).increment();
            log.info("payment_expired paymentId={} orderId={} attemptId={}",payment.getId(),orderId,attempt.getId());
        });
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-delay-ms:60000}")
    public void reconcile() {
        var orders = jdbc.query("""
                SELECT p.order_id FROM payments p WHERE p.status IN
                 ('SUCCESS','FAILED','CANCELLED','EXPIRED','REFUND_REQUESTED','REFUND_PROCESSING','REFUNDED','REFUND_FAILED')
                AND NOT EXISTS (SELECT 1 FROM payment_event_outbox o WHERE o.outcome_key='payment:'||p.id||':result')
                ORDER BY p.created_at LIMIT 100
                """, (rs,n)->rs.getObject(1,UUID.class));
        for (UUID orderId:orders) transactions.executeWithoutResult(tx -> {
            var p=payments.findByOrderIdForUpdate(orderId).orElseThrow();
            switch(p.getStatus()) {
                case SUCCESS, REFUND_REQUESTED, REFUND_PROCESSING, REFUNDED, REFUND_FAILED -> publisher.publishPaymentSuccess(p);
                case FAILED, CANCELLED -> publisher.publishPaymentFailed(p);
                case EXPIRED -> publisher.publishPaymentExpired(p);
                default -> { }
            }
        });
        var refundIds=jdbc.query("""
                SELECT r.id FROM payment_refunds r WHERE r.status IN ('REFUNDED','REFUND_FAILED','REFUND_MANUAL_REVIEW')
                AND NOT EXISTS(SELECT 1 FROM payment_event_outbox o WHERE o.outcome_key='refund:'||r.id||
                    CASE WHEN r.status='REFUNDED' THEN ':completed' ELSE ':failed' END)
                ORDER BY r.created_at LIMIT 100
                """,(rs,n)->rs.getObject(1,UUID.class));
        for(UUID id:refundIds) transactions.executeWithoutResult(tx->{
            UUID orderId=jdbc.queryForObject("SELECT p.order_id FROM payment_refunds r JOIN payments p ON p.id=r.payment_id WHERE r.id=?",UUID.class,id);
            var p=payments.findByOrderIdForUpdate(orderId).orElseThrow();
            var r=refunds.findByIdForUpdate(id).orElseThrow();
            if(r.getStatus()==RefundStatus.REFUNDED) {
                BigDecimal total=refunds.findByPayment_IdOrderByCreatedAtDesc(p.getId()).stream()
                        .filter(f->f.getStatus()==RefundStatus.REFUNDED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
                publisher.publishRefundCompleted(p,r,total);
            } else if(r.getStatus()==RefundStatus.REFUND_FAILED || r.getStatus().name().equals("REFUND_MANUAL_REVIEW")) publisher.publishRefundFailed(p,r);
        });
        deadCount.set(jdbc.queryForObject("SELECT count(*) FROM payment_event_outbox WHERE status='DEAD'",Long.class));
    }
}
