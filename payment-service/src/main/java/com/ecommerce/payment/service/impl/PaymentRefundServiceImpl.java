package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentRefundRequest;
import com.ecommerce.payment.dto.response.AdminRefundResponse;
import com.ecommerce.payment.dto.response.PaymentRefundResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.entity.PaymentRefund;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.enums.RefundStatus;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import com.ecommerce.payment.mapper.PaymentRefundMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentRefundService;
import com.ecommerce.payment.service.RefundAudit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Enqueues a refund atomically. Provider I/O is exclusively performed by the durable worker. */
@Service
@Validated
@RequiredArgsConstructor
@Transactional(noRollbackFor = PaymentApiException.class)
public class PaymentRefundServiceImpl implements PaymentRefundService {
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRefundMapper paymentRefundMapper;
    private final PaymentMetrics paymentMetrics;

    @Override
    public PaymentRefundResponse createPaymentRefund(@Valid CreatePaymentRefundRequest request) {
        UUID orderId = paymentRefundRepository.findPaymentOrderId(request.getPaymentId())
                .orElseThrow(() -> new PaymentApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        AdminRefundResponse response = refundPayment(request.getPaymentId(), orderId, request.getAmount(),
                request.getCurrency(), request.getReason(), request.getIdempotencyKey(), RefundAudit.system());
        return getPaymentRefundById(response.refundId());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRefundResponse getPaymentRefundById(UUID refundId) {
        return paymentRefundMapper.toResponse(paymentRefundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRefundResponse> getPaymentRefundsByPaymentId(UUID paymentId) {
        return paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(paymentId).stream()
                .map(paymentRefundMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRefundResponse getPaymentRefundByIdempotencyKey(String idempotencyKey) {
        return paymentRefundMapper.toResponse(paymentRefundRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return paymentRefundRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public AdminRefundResponse refundPayment(UUID paymentId, UUID orderId, BigDecimal amount, String currency,
                                              String reason, String idempotencyKey) {
        return refundPayment(paymentId, orderId, amount, currency, reason, idempotencyKey, RefundAudit.system());
    }

    @Override
    public AdminRefundResponse refundPayment(UUID paymentId, UUID orderId, BigDecimal amount, String currency,
                                              String reason, String idempotencyKey, RefundAudit audit) {
        validate(paymentId, orderId, amount, currency, idempotencyKey, reason);
        BigDecimal normalizedAmount;
        try {
            normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalidPrecision) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        // Every refund mutation locks the parent first, serializing amount reservations with webhooks.
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .filter(p -> p.getId().equals(paymentId))
                .orElseThrow(() -> new PaymentApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        PaymentRefund existing = paymentRefundRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getPayment().getId().equals(paymentId)
                    || existing.getAmount().compareTo(normalizedAmount) != 0
                    || !existing.getCurrency().equals(normalizedCurrency)
                    || !Objects.equals(existing.getReason(), normalizedReason)) {
                throw new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
            }
            return toAdminRefundResponse(existing);
        }
        if (!Set.of(PaymentStatus.SUCCESS, PaymentStatus.REFUND_REQUESTED,
                PaymentStatus.REFUND_PROCESSING, PaymentStatus.REFUND_FAILED).contains(payment.getStatus())
                || !payment.getCurrency().equals(normalizedCurrency)) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        BigDecimal reserved = paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(paymentId).stream()
                // Unknown provider acceptance retains its reservation until an operator reconciles it.
                .filter(r -> r.getStatus() != RefundStatus.REFUND_FAILED)
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reserved.add(normalizedAmount).compareTo(payment.getAmount()) > 0) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        PaymentAttempt attempt = paymentAttemptRepository.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(
                paymentId, List.of(PaymentAttemptStatus.SUCCESS))
                .orElseThrow(() -> new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED));
        if (attempt.getProviderPaymentIntentId() == null || attempt.getProviderPaymentIntentId().isBlank()) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        RefundAudit context = audit == null ? RefundAudit.system() : audit;
        PaymentRefund refund = PaymentRefund.builder().payment(payment).amount(normalizedAmount)
                .currency(normalizedCurrency).reason(normalizedReason).status(RefundStatus.REFUND_REQUESTED)
                .idempotencyKey(idempotencyKey).providerIdempotencyKey("refund:" + UUID.randomUUID())
                .providerPaymentIntentId(attempt.getProviderPaymentIntentId())
                .refundRequestId(context.refundRequestId()).requestedBy(context.requestedBy())
                .actorType(context.actorType()).correlationId(context.correlationId()).traceId(context.traceId())
                .requestedAt(context.requestedAt() == null ? Instant.now() : context.requestedAt())
                .nextAttemptAt(Instant.now()).build();
        PaymentRefund saved = paymentRefundRepository.saveAndFlush(refund);
        payment.setStatus(PaymentStatus.REFUND_REQUESTED);
        payment.setFailureReason(null);
        paymentRepository.save(payment);
        paymentMetrics.refundRequested(payment.getProvider());
        return toAdminRefundResponse(saved);
    }

    @Override
    public AdminRefundResponse reconcileRefund(UUID paymentId, UUID refundId, UUID actorId, String reason) {
        if (actorId == null || reason == null || reason.isBlank() || reason.length() > 5000) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_INVALID_REQUEST);
        }
        UUID orderId = paymentRefundRepository.findOrderId(paymentId, refundId)
                .orElseThrow(() -> new PaymentApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElseThrow();
        PaymentRefund refund = paymentRefundRepository.findByIdForUpdate(refundId).orElseThrow();
        if (refund.getStatus() == RefundStatus.REFUNDED) return toAdminRefundResponse(refund);
        if (refund.getStatus() == RefundStatus.REFUND_REQUESTED || refund.getStatus() == RefundStatus.REFUND_PROCESSING) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_IN_PROGRESS);
        }
        // Known failed provider results require a new, explicitly authorized refund request.
        boolean hasProviderId = refund.getProviderRefundId() != null && !refund.getProviderRefundId().isBlank();
        if (refund.getStatus() != RefundStatus.REFUND_MANUAL_REVIEW
                || (!hasProviderId && (refund.getFirstProviderAttemptAt() == null
                || !refund.getFirstProviderAttemptAt().isAfter(Instant.now().minusSeconds(23 * 3600))))) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
        refund.setStatus(hasProviderId ? RefundStatus.REFUND_PROCESSING : RefundStatus.REFUND_REQUESTED);
        refund.setAttemptCount(0);
        refund.setNextAttemptAt(Instant.now());
        refund.setLeaseToken(null);
        refund.setLeaseUntil(null);
        refund.setFailureReason(null);
        refund.setLastReconciledBy(actorId);
        refund.setLastReconciledAt(Instant.now());
        refund.setReconciliationReason(reason.trim());
        refund.setReconciliationCount(refund.getReconciliationCount() + 1);
        payment.setStatus(PaymentStatus.REFUND_PROCESSING);
        payment.setFailureReason(null);
        // Original provider key, first acceptance-attempt time and request audit are immutable.
        return toAdminRefundResponse(refund);
    }
    private void validate(UUID paymentId, UUID orderId, BigDecimal amount, String currency,
                          String key, String reason) {
        if (paymentId == null || orderId == null || amount == null || amount.signum() <= 0
                || currency == null || !currency.trim().matches("(?i)[a-z]{3}")
                || key == null || key.isBlank() || key.length() > 150
                || (reason != null && reason.length() > 5000)) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_REFUND_NOT_ALLOWED);
        }
    }

    private AdminRefundResponse toAdminRefundResponse(PaymentRefund refund) {
        return new AdminRefundResponse(refund.getPayment().getId(), refund.getId(), refund.getStatus().name(),
                refund.getProviderRefundId(), refund.getFailureReason());
    }
}
