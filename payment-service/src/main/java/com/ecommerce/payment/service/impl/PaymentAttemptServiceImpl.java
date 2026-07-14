package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentAttemptRequest;
import com.ecommerce.payment.dto.response.PaymentAttemptResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentProvider;
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
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentAttemptServiceImpl implements PaymentAttemptService {

    private static final List<PaymentProvider> PROVIDER_LOOKUP_ORDER = List.of(
            PaymentProvider.STRIPE,
            PaymentProvider.SANDBOX,
            PaymentProvider.RAZORPAY
    );

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
        validateProviderLookupValue(
                providerSessionId,
                "Provider session id is required"
        );

        PaymentAttempt attempt = findByProviderSessionId(providerSessionId)
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
        validateProviderLookupValue(
                providerPaymentIntentId,
                "Provider payment intent id is required"
        );

        PaymentAttempt attempt = findByProviderPaymentIntentId(providerPaymentIntentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment attempt not found for provider payment intent id: "
                                + providerPaymentIntentId
                ));

        return paymentAttemptMapper.toResponse(attempt);
    }

    private Optional<PaymentAttempt> findByProviderSessionId(String providerSessionId) {
        for (PaymentProvider provider : PROVIDER_LOOKUP_ORDER) {
            Optional<PaymentAttempt> attempt =
                    paymentAttemptRepository.findByProviderAndProviderSessionId(
                            provider,
                            providerSessionId
                    );

            if (attempt.isPresent()) {
                return attempt;
            }
        }

        return Optional.empty();
    }

    private Optional<PaymentAttempt> findByProviderPaymentIntentId(
            String providerPaymentIntentId
    ) {
        for (PaymentProvider provider : PROVIDER_LOOKUP_ORDER) {
            Optional<PaymentAttempt> attempt =
                    paymentAttemptRepository.findByProviderAndProviderPaymentIntentId(
                            provider,
                            providerPaymentIntentId
                    );

            if (attempt.isPresent()) {
                return attempt;
            }
        }

        return Optional.empty();
    }

    private void validateProviderLookupValue(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
    }
}