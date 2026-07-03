package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.dto.request.CreatePaymentWebhookEventRequest;
import com.ecommerce.payment.dto.response.PaymentWebhookEventResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentWebhookEvent;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import com.ecommerce.payment.mapper.PaymentWebhookEventMapper;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.repository.PaymentWebhookEventRepository;
import com.ecommerce.payment.service.PaymentWebhookEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentWebhookEventServiceImpl implements PaymentWebhookEventService {

    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventMapper paymentWebhookEventMapper;

    @Override
    public PaymentWebhookEventResponse createPaymentWebhookEvent(
            @Valid CreatePaymentWebhookEventRequest request
    ) {
        if (paymentWebhookEventRepository.existsByProviderAndProviderEventId(
                request.getProvider(),
                request.getProviderEventId()
        )) {
            throw new ResourceAlreadyExistsException(
                    "Webhook event already exists. provider="
                            + request.getProvider()
                            + ", providerEventId="
                            + request.getProviderEventId()
            );
        }

        PaymentWebhookEvent webhookEvent = paymentWebhookEventMapper.toEntity(request);

        if (request.getPaymentId() != null) {
            Payment payment = paymentRepository.findById(request.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Payment not found: " + request.getPaymentId()
                    ));

            webhookEvent.setPayment(payment);
        }

        PaymentWebhookEvent savedEvent = paymentWebhookEventRepository.save(webhookEvent);
        return paymentWebhookEventMapper.toResponse(savedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentWebhookEventResponse getPaymentWebhookEventById(UUID webhookEventId) {
        PaymentWebhookEvent event = getWebhookEventEntityById(webhookEventId);
        return paymentWebhookEventMapper.toResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentWebhookEventResponse getPaymentWebhookEventByProviderEventId(
            PaymentProvider provider,
            String providerEventId
    ) {
        PaymentWebhookEvent event = paymentWebhookEventRepository
                .findByProviderAndProviderEventId(provider, providerEventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Webhook event not found. provider="
                                + provider
                                + ", providerEventId="
                                + providerEventId
                ));

        return paymentWebhookEventMapper.toResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProviderAndProviderEventId(
            PaymentProvider provider,
            String providerEventId
    ) {
        return paymentWebhookEventRepository.existsByProviderAndProviderEventId(
                provider,
                providerEventId
        );
    }

    @Override
    public PaymentWebhookEventResponse markProcessed(UUID webhookEventId) {
        PaymentWebhookEvent event = getWebhookEventEntityById(webhookEventId);
        event.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
        event.setProcessedAt(LocalDateTime.now());

        PaymentWebhookEvent savedEvent = paymentWebhookEventRepository.save(event);
        return paymentWebhookEventMapper.toResponse(savedEvent);
    }

    @Override
    public PaymentWebhookEventResponse markIgnored(UUID webhookEventId) {
        PaymentWebhookEvent event = getWebhookEventEntityById(webhookEventId);
        event.setProcessingStatus(WebhookProcessingStatus.IGNORED);
        event.setProcessedAt(LocalDateTime.now());

        PaymentWebhookEvent savedEvent = paymentWebhookEventRepository.save(event);
        return paymentWebhookEventMapper.toResponse(savedEvent);
    }

    @Override
    public PaymentWebhookEventResponse markFailed(UUID webhookEventId) {
        PaymentWebhookEvent event = getWebhookEventEntityById(webhookEventId);
        event.setProcessingStatus(WebhookProcessingStatus.FAILED);
        event.setProcessedAt(LocalDateTime.now());

        PaymentWebhookEvent savedEvent = paymentWebhookEventRepository.save(event);
        return paymentWebhookEventMapper.toResponse(savedEvent);
    }

    private PaymentWebhookEvent getWebhookEventEntityById(UUID webhookEventId) {
        return paymentWebhookEventRepository.findById(webhookEventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment webhook event not found: " + webhookEventId
                ));
    }
}