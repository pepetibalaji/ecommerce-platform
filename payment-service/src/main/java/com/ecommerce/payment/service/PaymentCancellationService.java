package com.ecommerce.payment.service;

import com.ecommerce.common.events.core.EventSources;
import com.ecommerce.common.events.payment.PaymentCancellationRequestedEvent;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentCancellationRequest;
import com.ecommerce.payment.entity.PaymentRefund;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.enums.RefundStatus;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentCancellationRequestRepository;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationService {
    private final PaymentCancellationRequestRepository requests;
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentRefundRepository refunds;
    private final PaymentRefundService refundService;
    private final PaymentEventPublisher publisher;
    private final MeterRegistry metrics;
    private final PaymentCancellationPreparationService preparation;

    @Transactional
    public void enqueue(PaymentCancellationRequestedEvent event) {
        if (event == null || event.getCancellationRequestId() == null || event.getOrderId() == null
                || event.getUserId() == null || event.getAmount() == null || event.getAmount().signum() <= 0
                || event.getAmount().stripTrailingZeros().scale() > 2 || event.getAmount().precision() > 19
                || (event.getReason() != null && event.getReason().length() > 5000)
                || (event.getActorType() != null && event.getActorType().length() > 40)
                || (event.getCorrelationId() != null && event.getCorrelationId().length() > 128)
                || (event.getTraceId() != null && event.getTraceId().length() > 128)
                || event.getCurrency() == null || !event.getCurrency().matches("[A-Z]{3}")
                || !"1.0".equals(event.getSchemaVersion()) || !EventSources.ORDER_SERVICE.equals(event.getSource())
                || event.getOccurredAt() == null || event.getOccurredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new IllegalArgumentException("Invalid payment cancellation command");
        }
        PaymentCancellationRequest existing = requests.findById(event.getCancellationRequestId()).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(event.getOrderId()) || !existing.getUserId().equals(event.getUserId())
                    || existing.getAmount().compareTo(event.getAmount()) != 0
                    || !existing.getCurrency().equals(event.getCurrency())
                    || existing.isExpiryRequested() != event.isExpiryRequested()
                    || !Objects.equals(existing.getReason(), event.getReason())) {
                throw new IllegalArgumentException("Conflicting payment cancellation command");
            }
            return;
        }
        Instant now = Instant.now();
        requests.saveAndFlush(PaymentCancellationRequest.builder().id(event.getCancellationRequestId())
                .orderId(event.getOrderId()).userId(event.getUserId()).amount(event.getAmount())
                .currency(event.getCurrency()).requestedBy(event.getRequestedBy()).actorType(event.getActorType())
                .reason(event.getReason()).correlationId(event.getCorrelationId()).traceId(event.getTraceId())
                .expiryRequested(event.isExpiryRequested()).requestedAt(event.getOccurredAt()).receivedAt(now)
                .status("REQUESTED").nextAttemptAt(now).build());
    }

    @Transactional
    public boolean processNext() {
        var due = requests.findNextForUpdate(Instant.now());
        if (due.isEmpty()) return false;
        PaymentCancellationRequest request = due.getFirst();
        request.setAttemptCount(request.getAttemptCount() + 1);
        Payment payment = payments.findByOrderIdForUpdate(request.getOrderId()).orElse(null);
        if (payment == null) {
            try {
                // Recovers even when order-created delivery exhausted before this tombstone arrived.
                // Preparation independently verifies the immutable Order snapshot and commits first.
                preparation.prepare(request);
                payment = payments.findByOrderIdForUpdate(request.getOrderId()).orElse(null);
            } catch (RuntimeException lookupFailure) {
                log.warn("payment_cancellation_preparation_retry cancellationRequestId={} orderId={} exceptionType={}",
                        request.getId(), request.getOrderId(), lookupFailure.getClass().getSimpleName());
            }
            if (payment == null) {
                awaitOutcome(request, "PAYMENT_PREPARING");
                return true;
            }
        }
        if (!payment.getUserId().equals(request.getUserId())
                || payment.getAmount().compareTo(request.getAmount()) != 0
                || !payment.getCurrency().equals(request.getCurrency())) {
            manualReview(request, "PAYMENT_STATE_CONFLICT");
            return true;
        }
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            // An in-flight charge is resolved only by a verified provider event, then this command retries.
            awaitOutcome(request, "PAYMENT_PROCESSING");
            return true;
        }
        if (Set.of(PaymentStatus.PENDING, PaymentStatus.REQUIRES_CUSTOMER_ACTION).contains(payment.getStatus())) {
            boolean expiry = request.isExpiryRequested();
            payment.setStatus(expiry ? PaymentStatus.EXPIRED : PaymentStatus.CANCELLED);
            payment.setFailureReason(expiry ? "PAYMENT_EXPIRED" : "PAYMENT_CANCELLED");
            attempts.findByPayment_IdOrderByCreatedAtDesc(payment.getId()).stream()
                    .filter(a -> Set.of(PaymentAttemptStatus.CREATED, PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION,
                            PaymentAttemptStatus.PROCESSING).contains(a.getStatus()))
                    .forEach(a -> a.setStatus(expiry ? PaymentAttemptStatus.EXPIRED : PaymentAttemptStatus.CANCELLED));
            if (expiry) publisher.publishPaymentExpired(payment); else publisher.publishPaymentFailed(payment);
            completed(request);
        } else if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUND_FAILED) {
            if (request.isExpiryRequested()) {
                publisher.publishPaymentSuccess(payment);
                completed(request);
                return true;
            }
            BigDecimal refunded = refunds.findByPayment_IdOrderByCreatedAtDesc(payment.getId()).stream()
                    .filter(r -> r.getStatus() == RefundStatus.REFUNDED).map(PaymentRefund::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            try {
                refundService.refundPayment(payment.getId(), payment.getOrderId(), payment.getAmount().subtract(refunded),
                    payment.getCurrency(), request.getReason(), "order-refund-request:" + request.getId(),
                    new RefundAudit(request.getId(), request.getRequestedBy(), request.getActorType(),
                            request.getCorrelationId(), request.getTraceId(), request.getRequestedAt()));
                completed(request);
            } catch (com.ecommerce.payment.exception.PaymentApiException refused) {
                manualReview(request, refused.getCode().name());
            }
        } else if (Set.of(PaymentStatus.REFUND_REQUESTED, PaymentStatus.REFUND_PROCESSING).contains(payment.getStatus())) {
            awaitOutcome(request, "PAYMENT_REFUND_IN_PROGRESS");
        } else {
            if (payment.getStatus() == PaymentStatus.CANCELLED || payment.getStatus() == PaymentStatus.FAILED) {
                publisher.publishPaymentFailed(payment);
            } else if (payment.getStatus() == PaymentStatus.EXPIRED) {
                publisher.publishPaymentExpired(payment);
            } else if (payment.getStatus() == PaymentStatus.REFUNDED) {
                var successful = refunds.findByPayment_IdOrderByCreatedAtDesc(payment.getId()).stream()
                        .filter(r -> r.getStatus() == RefundStatus.REFUNDED).findFirst();
                if (successful.isPresent()) publisher.publishRefundCompleted(payment, successful.get(), payment.getAmount());
            }
            completed(request);
        }
        return true;
    }

    private void awaitOutcome(PaymentCancellationRequest request, String code) {
        if (request.getReceivedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            manualReview(request, code);
            return;
        }
        request.setStatus("WAITING_PROVIDER");
        request.setFailureCode(code);
        request.setNextAttemptAt(Instant.now().plusSeconds(30));
    }

    private void manualReview(PaymentCancellationRequest request, String code) {
        request.setStatus("MANUAL_REVIEW");
        request.setFailureCode(code);
        metrics.counter("payment.cancellation.manual_review.count").increment();
        log.error("payment_cancellation_manual_review cancellationRequestId={} orderId={} code={}",
                request.getId(), request.getOrderId(), code);
    }

    private void completed(PaymentCancellationRequest request) {
        request.setStatus("COMPLETED");
        request.setFailureCode(null);
        request.setCompletedAt(Instant.now());
    }
}
