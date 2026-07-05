package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import com.ecommerce.payment.dto.response.AdminPaymentResponse;
import com.ecommerce.payment.dto.response.PaymentAttemptResponse;
import com.ecommerce.payment.dto.response.PaymentRefundResponse;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.mapper.PaymentAttemptMapper;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.mapper.PaymentRefundMapper;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRepository paymentRepository;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final PaymentRefundRepository paymentRefundRepository;

    private final PaymentMapper paymentMapper;

    private final PaymentAttemptMapper paymentAttemptMapper;

    private final PaymentRefundMapper paymentRefundMapper;

    @Override
    public Page<PaymentResponse> getMyPayments(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toResponse);
    }

    @Override
    public PaymentResponse getPaymentByOrderIdForUser(UUID orderId, UUID userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId
                ));

        validateOwnership(payment, userId);

        return paymentMapper.toResponse(payment);
    }

    @Override
    public PaymentResponse getPaymentByIdForUser(UUID paymentId, UUID userId) {
        Payment payment = getPaymentEntity(paymentId);
        validateOwnership(payment, userId);
        return paymentMapper.toResponse(payment);
    }

    @Override
    public Page<AdminPaymentResponse> getAdminPayments(PaymentStatus status, Pageable pageable) {
        Page<Payment> payments = status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatus(status, pageable);

        return payments.map(this::toAdminPaymentResponse);
    }

    @Override
    public AdminPaymentResponse getAdminPaymentById(UUID paymentId) {
        Payment payment = getPaymentEntity(paymentId);
        return toAdminPaymentResponse(payment);
    }

    private Payment getPaymentEntity(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + paymentId
                ));
    }

    private AdminPaymentResponse toAdminPaymentResponse(Payment payment) {
        PaymentAttemptResponse latestAttempt = paymentAttemptRepository
                .findTopByPayment_IdOrderByCreatedAtDesc(payment.getId())
                .map(paymentAttemptMapper::toResponse)
                .orElse(null);

        List<PaymentRefundResponse> refunds = paymentRefundRepository
                .findByPayment_IdOrderByCreatedAtDesc(payment.getId())
                .stream()
                .map(paymentRefundMapper::toResponse)
                .toList();

        return AdminPaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .failureReason(payment.getFailureReason())
                .latestAttempt(latestAttempt)
                .refunds(refunds)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private void validateOwnership(Payment payment, UUID userId) {
        if (!payment.getUserId().equals(userId)) {
            throw new UnauthorizedException(
                    "User is not allowed to access payment: " + payment.getId()
            );
        }
    }
}