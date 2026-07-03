package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentRefundRequest;
import com.ecommerce.payment.dto.response.PaymentRefundResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentRefund;
import com.ecommerce.payment.mapper.PaymentRefundMapper;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundMapper paymentRefundMapper;

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
}