package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentAttemptRequest;
import com.ecommerce.payment.dto.response.PaymentAttemptResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.mapper.PaymentAttemptMapper;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentAttemptService;
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
public class PaymentAttemptServiceImpl implements PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptMapper paymentAttemptMapper;

    @Override
    public PaymentAttemptResponse createPaymentAttempt(@Valid CreatePaymentAttemptRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + request.getPaymentId()
                ));

        PaymentAttempt paymentAttempt = paymentAttemptMapper.toEntity(request);
        paymentAttempt.setPayment(payment);

        PaymentAttempt savedAttempt = paymentAttemptRepository.save(paymentAttempt);
        return paymentAttemptMapper.toResponse(savedAttempt);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAttemptResponse getPaymentAttemptById(UUID paymentAttemptId) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentAttemptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment attempt not found: " + paymentAttemptId
                ));

        return paymentAttemptMapper.toResponse(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentAttemptResponse> getPaymentAttemptsByPaymentId(UUID paymentId) {
        return paymentAttemptRepository.findByPayment_IdOrderByCreatedAtDesc(paymentId)
                .stream()
                .map(paymentAttemptMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAttemptResponse getLatestPaymentAttemptByPaymentId(UUID paymentId) {
        PaymentAttempt attempt = paymentAttemptRepository
                .findTopByPayment_IdOrderByCreatedAtDesc(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment attempts found for payment: " + paymentId
                ));

        return paymentAttemptMapper.toResponse(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAttemptResponse getPaymentAttemptByProviderSessionId(String providerSessionId) {
        PaymentAttempt attempt = paymentAttemptRepository.findByProviderSessionId(providerSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment attempt not found for provider session id: " + providerSessionId
                ));

        return paymentAttemptMapper.toResponse(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAttemptResponse getPaymentAttemptByProviderPaymentIntentId(
            String providerPaymentIntentId
    ) {
        PaymentAttempt attempt = paymentAttemptRepository
                .findByProviderPaymentIntentId(providerPaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment attempt not found for provider payment intent id: "
                                + providerPaymentIntentId
                ));

        return paymentAttemptMapper.toResponse(attempt);
    }
}