package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentRequest;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final String ORDER_CREATED_IDEMPOTENCY_PREFIX = "order-created:";

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentMetrics paymentMetrics;
    private final com.ecommerce.payment.order.TrustedOrderClient trustedOrderClient;
    private final com.ecommerce.payment.repository.PaymentCancellationRequestRepository cancellationRequests;

    @Override
    public PaymentResponse createPayment(@Valid CreatePaymentRequest request) {
        // Only the authenticated Order event pipeline may prepare payments.
        throw new BadRequestException("Payments must be prepared from an Order checkout");
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId) {
        Payment payment = getPaymentEntityById(paymentId);

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId
                ));

        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByUserId(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByStatus(status, pageable)
                .map(paymentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrderId(UUID orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public void preparePaymentFromOrder(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            String correlationId,
            String traceId
    ) {
        validateOrderCreatedPaymentInput(
                orderId,
                userId,
                amount,
                currency
        );

        String normalizedCurrency = normalizeCurrency(currency);
        BigDecimal normalizedAmount = normalizeAmount(amount);
        String idempotencyKey = buildOrderCreatedIdempotencyKey(orderId);

        var existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            var prepared = existing.get();
            if (!userId.equals(prepared.getUserId()) || normalizedAmount.compareTo(prepared.getAmount()) != 0
                    || !normalizedCurrency.equals(prepared.getCurrency())) {
                throw new BadRequestException("Duplicate Order event does not match prepared payment");
            }
            return;
        }
        trustedOrderClient.validatePreparation(orderId, userId, normalizedAmount, normalizedCurrency,
                cancellationRequests.existsByOrderId(orderId));

        Payment payment = Payment.builder()
        .orderId(orderId)
        .userId(userId)
        .amount(normalizedAmount)
        .currency(normalizedCurrency)
        .status(PaymentStatus.PENDING)
        .provider(PaymentProvider.STRIPE)
        .idempotencyKey(idempotencyKey)
        .correlationId(correlationId)
        .traceId(traceId)
        .failureReason(null)
        .build();

        try {
            Payment saved = paymentRepository.saveAndFlush(payment);
            paymentMetrics.paymentCreated();

            log.info(
                    "Prepared PENDING payment from order-created event. orderId={}, paymentId={}, amount={}, currency={}, idempotencyKey={}, correlationId={}, traceId={}",
                    orderId,
                    saved.getId(),
                    normalizedAmount,
                    normalizedCurrency,
                    idempotencyKey,
                    correlationId,
                    traceId
            );
        } catch (DataIntegrityViolationException ex) {
            // PostgreSQL aborts a transaction after a unique violation. Let Kafka retry in a new
            // transaction; do not query or acknowledge from this rollback-only transaction.
            throw ex;
        }
    }

    private Payment getPaymentEntityById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + paymentId
                ));
    }

    private void validateOrderCreatedPaymentInput(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency
    ) {
        if (orderId == null) {
            throw new BadRequestException("orderId is required for payment preparation");
        }

        if (userId == null) {
            throw new BadRequestException("userId is required for payment preparation");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.precision() - amount.scale() > 17) {
            throw new BadRequestException("amount must be greater than zero for payment preparation");
        }

        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("currency is required for payment preparation");
        }
    }

    private String normalizeCurrency(String currency) {
        String normalized = currency.trim().toUpperCase();

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new BadRequestException(
                    "currency must be a 3-letter ISO code, for example INR or USD"
            );
        }

        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BadRequestException("amount must have at most 2 decimal places");
        }
    }

    private String buildOrderCreatedIdempotencyKey(UUID orderId) {
        return ORDER_CREATED_IDEMPOTENCY_PREFIX + orderId;
    }

}
