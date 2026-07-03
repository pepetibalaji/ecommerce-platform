package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentRequest;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public PaymentResponse createPayment(@Valid CreatePaymentRequest request) {
        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new ResourceAlreadyExistsException(
                    "Payment already exists for order: " + request.getOrderId()
            );
        }

        if (paymentRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new ResourceAlreadyExistsException(
                    "Payment already exists for idempotency key: " + request.getIdempotencyKey()
            );
        }

        Payment payment = paymentMapper.toEntity(request);
        payment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);
        return paymentMapper.toResponse(savedPayment);
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

    private Payment getPaymentEntityById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + paymentId
                ));
    }
}