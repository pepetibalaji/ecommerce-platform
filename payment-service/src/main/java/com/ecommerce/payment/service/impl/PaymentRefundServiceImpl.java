package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
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
import com.ecommerce.payment.mapper.PaymentRefundMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.RefundGatewayRequest;
import com.ecommerce.payment.provider.model.RefundGatewayResponse;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentRefundService;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final PaymentRefundMapper paymentRefundMapper;
    private final PaymentMetrics paymentMetrics;
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    public PaymentRefundResponse createPaymentRefund(@Valid CreatePaymentRefundRequest request) {
        if (paymentRefundRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new ResourceAlreadyExistsException(
                    "Payment refund already exists for idempotency key: "
                            + request.getIdempotencyKey()
            );
        }

        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + request.getPaymentId()
                ));

        PaymentRefund refund = paymentRefundMapper.toEntity(request);
        refund.setPayment(payment);

        PaymentRefund savedRefund = paymentRefundRepository.save(refund);

        return paymentRefundMapper.toResponse(savedRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRefundResponse getPaymentRefundById(UUID refundId) {
        PaymentRefund refund = paymentRefundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment refund not found: " + refundId
                ));

        return paymentRefundMapper.toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRefundResponse> getPaymentRefundsByPaymentId(UUID paymentId) {
        return paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(paymentId)
                .stream()
                .map(paymentRefundMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRefundResponse getPaymentRefundByIdempotencyKey(String idempotencyKey) {
        PaymentRefund refund = paymentRefundRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment refund not found for idempotency key: " + idempotencyKey
                ));

        return paymentRefundMapper.toResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return paymentRefundRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public AdminRefundResponse refundPayment(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey
    ) {
        validateRefundRequest(
                paymentId,
                orderId,
                amount,
                currency,
                idempotencyKey
        );

        BigDecimal normalizedAmount = normalizeAmount(amount);
        String normalizedCurrency = normalizeCurrency(currency);
        String normalizedReason = normalizeReason(reason);

        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for paymentId=" + paymentId + ", orderId=" + orderId
                ));

        PaymentRefund existingRefund = paymentRefundRepository
                .findByPayment_IdAndIdempotencyKey(paymentId, idempotencyKey)
                .orElse(null);

        if (existingRefund != null) {
            log.info(
                    "Returning existing refund for idempotency key. paymentId={}, refundId={}, idempotencyKey={}",
                    paymentId,
                    existingRefund.getId(),
                    idempotencyKey
            );

            return toAdminRefundResponse(existingRefund);
        }

        validateRefundEligibility(
                payment,
                normalizedAmount,
                normalizedCurrency
        );

        PaymentAttempt successfulAttempt = paymentAttemptRepository
                .findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(
                        payment.getId(),
                        List.of(PaymentAttemptStatus.SUCCESS)
                )
                .orElseThrow(() -> new BadRequestException(
                        "No successful payment attempt found for payment: " + payment.getId()
                ));

        validateProviderPaymentIntent(successfulAttempt);

        validateRefundDoesNotExceedPaymentAmount(
                payment,
                normalizedAmount
        );

        PaymentRefund refund = PaymentRefund.builder()
                .payment(payment)
                .amount(normalizedAmount)
                .currency(normalizedCurrency)
                .reason(normalizedReason)
                .status(RefundStatus.REFUND_REQUESTED)
                .idempotencyKey(idempotencyKey)
                .build();

        paymentRefundRepository.saveAndFlush(refund);
        paymentMetrics.refundRequested(payment.getProvider());

        payment.setStatus(PaymentStatus.REFUND_REQUESTED);
        payment.setFailureReason(null);
        paymentRepository.saveAndFlush(payment);

        RefundGatewayResponse providerResponse = requestProviderRefund(
                payment,
                successfulAttempt,
                normalizedAmount,
                normalizedCurrency,
                normalizedReason,
                idempotencyKey
        );

        refund.setProviderRefundId(providerResponse.providerRefundId());
        refund.setFailureReason(providerResponse.failureReason());

        if (providerResponse.success()) {
            applySuccessfulProviderRefundResponse(
                    payment,
                    refund,
                    providerResponse
            );
        } else {
            refund.setStatus(RefundStatus.REFUND_FAILED);
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            payment.setFailureReason(providerResponse.failureReason());
        }

        if (providerResponse.success()) {
            paymentMetrics.refundSucceeded(payment.getProvider());
        } else {
            paymentMetrics.refundFailed(payment.getProvider());
        }

        PaymentRefund savedRefund = paymentRefundRepository.save(refund);
        paymentRepository.save(payment);

        if (savedRefund.getStatus() == RefundStatus.REFUNDED) {
            paymentEventPublisher.publishRefundCompleted(payment, savedRefund,
                    totalSuccessfulRefunds(payment));
        }

        log.info(
                "Refund request processed. paymentId={}, refundId={}, orderId={}, refundStatus={}, paymentStatus={}, providerRefundId={}",
                payment.getId(),
                savedRefund.getId(),
                payment.getOrderId(),
                savedRefund.getStatus(),
                payment.getStatus(),
                savedRefund.getProviderRefundId()
        );

        return toAdminRefundResponse(savedRefund);
    }

    private RefundGatewayResponse requestProviderRefund(
            Payment payment,
            PaymentAttempt successfulAttempt,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey
    ) {
        try {
            PaymentGateway gateway = paymentGatewayFactory.getGateway(payment.getProvider());

            return gateway.refund(
                    new RefundGatewayRequest(
                            payment.getId(),
                            payment.getOrderId(),
                            successfulAttempt.getProviderPaymentIntentId(),
                            amount,
                            currency,
                            reason,
                            idempotencyKey
                    )
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Provider refund request failed. paymentId={}, orderId={}, provider={}, reason={}",
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getProvider(),
                    exception.getMessage()
            );

            return new RefundGatewayResponse(
                    false,
                    null,
                    "FAILED",
                    exception.getMessage()
            );
        }
    }

    private void validateRefundRequest(
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
        if (paymentId == null) {
            throw new BadRequestException("paymentId is required");
        }

        if (orderId == null) {
            throw new BadRequestException("orderId is required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than zero");
        }

        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Refund currency is required");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Refund idempotency key is required");
        }
    }

    private void validateRefundEligibility(
            Payment payment,
            BigDecimal refundAmount,
            String refundCurrency
    ) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BadRequestException("Only SUCCESS payments can be refunded");
        }

        if (!payment.getCurrency().equals(refundCurrency)) {
            throw new BadRequestException(
                    "Refund currency must match payment currency: " + payment.getCurrency()
            );
        }

        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Refund amount cannot exceed payment amount");
        }
    }

    private void validateProviderPaymentIntent(PaymentAttempt successfulAttempt) {
        if (successfulAttempt.getProviderPaymentIntentId() == null
                || successfulAttempt.getProviderPaymentIntentId().isBlank()) {
            throw new BadRequestException(
                    "Payment cannot be refunded because provider payment intent id is missing"
            );
        }
    }

    private void validateRefundDoesNotExceedPaymentAmount(
            Payment payment,
            BigDecimal newRefundAmount
    ) {
        BigDecimal alreadyRequestedOrProcessed = paymentRefundRepository
                .findByPayment_IdOrderByCreatedAtDesc(payment.getId())
                .stream()
                .filter(refund -> refund.getStatus() != RefundStatus.REFUND_FAILED)
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAfterNewRefund = alreadyRequestedOrProcessed.add(newRefundAmount);

        if (totalAfterNewRefund.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Total refund amount cannot exceed payment amount");
        }
    }

    private void applySuccessfulProviderRefundResponse(
            Payment payment,
            PaymentRefund refund,
            RefundGatewayResponse providerResponse
    ) {
        if (isTerminalRefundSuccess(providerResponse.status())) {
            refund.setStatus(RefundStatus.REFUNDED);
            payment.setStatus(isFullRefundAfter(refund, payment) ? PaymentStatus.REFUNDED : PaymentStatus.SUCCESS);
            payment.setFailureReason(null);
            return;
        }

        refund.setStatus(RefundStatus.REFUND_PROCESSING);
        payment.setStatus(PaymentStatus.REFUND_PROCESSING);
        payment.setFailureReason(null);
    }

    private boolean isFullRefundAfter(PaymentRefund newRefund, Payment payment) {
        return totalSuccessfulRefunds(payment).add(newRefund.getAmount()).compareTo(payment.getAmount()) == 0;
    }

    private BigDecimal totalSuccessfulRefunds(Payment payment) {
        return paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(payment.getId()).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.REFUNDED)
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isTerminalRefundSuccess(String providerStatus) {
        if (providerStatus == null) {
            return false;
        }

        String normalized = providerStatus.trim().toLowerCase();

        return normalized.equals("succeeded")
                || normalized.equals("success")
                || normalized.equals("refunded");
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("Refund amount must have at most 2 decimal places");
        }
    }

    private String normalizeCurrency(String currency) {
        String normalized = currency.trim().toUpperCase();

        if (!normalized.matches("^[A-Z]{3}$")) {
            throw new BadRequestException(
                    "Currency must be a 3-letter ISO code, for example INR or USD"
            );
        }

        return normalized;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }

        return reason.trim();
    }

    private AdminRefundResponse toAdminRefundResponse(PaymentRefund refund) {
        return new AdminRefundResponse(
                refund.getPayment().getId(),
                refund.getId(),
                refund.getStatus().name(),
                refund.getProviderRefundId(),
                refund.getFailureReason()
        );
    }
}
