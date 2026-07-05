package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.dto.response.WebhookAckResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.entity.PaymentWebhookEvent;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.repository.PaymentWebhookEventRepository;
import com.ecommerce.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final PaymentGatewayFactory paymentGatewayFactory;

    private final PaymentRepository paymentRepository;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Override
    public WebhookAckResponse processWebhook(
            PaymentProvider provider,
            String payload,
            String signature
    ) {
        PaymentGateway gateway = paymentGatewayFactory.getGateway(provider);

        ProviderWebhookEvent providerEvent = gateway.parseWebhookEvent(payload, signature);

        Optional<PaymentAttempt> attemptOptional = resolveAttempt(providerEvent);

        UUID paymentId = attemptOptional
                .map(PaymentAttempt::getPayment)
                .map(Payment::getId)
                .orElse(null);

        int insertedRows = paymentWebhookEventRepository.insertReceivedEventIfAbsent(
                UUID.randomUUID(),
                providerEvent.getProvider().name(),
                providerEvent.getProviderEventId(),
                paymentId,
                providerEvent.getEventType(),
                WebhookProcessingStatus.RECEIVED.name(),
                null,
                LocalDateTime.now(),
                null
        );

        if (insertedRows == 0) {
            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(true)
                    .processingStatus(WebhookProcessingStatus.IGNORED)
                    .message("Duplicate webhook ignored")
                    .build();
        }

        PaymentWebhookEvent savedWebhookEvent = paymentWebhookEventRepository
                .findByProviderAndProviderEventId(
                        providerEvent.getProvider(),
                        providerEvent.getProviderEventId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook event was inserted but could not be loaded. provider="
                                + providerEvent.getProvider()
                                + ", providerEventId="
                                + providerEvent.getProviderEventId()
                ));

        if (ProviderPaymentStatus.IGNORED == providerEvent.getStatus()) {
            markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.IGNORED);

            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(false)
                    .processingStatus(WebhookProcessingStatus.IGNORED)
                    .message("Webhook event ignored")
                    .build();
        }

        if (attemptOptional.isEmpty()) {
            markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.FAILED);

            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(false)
                    .processingStatus(WebhookProcessingStatus.FAILED)
                    .message("Payment attempt could not be resolved")
                    .build();
        }

        PaymentAttempt attempt = attemptOptional.get();
        Payment payment = attempt.getPayment();

        applyProviderState(providerEvent, payment, attempt);

        paymentAttemptRepository.save(attempt);
        paymentRepository.save(payment);

        markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.PROCESSED);

        return WebhookAckResponse.builder()
                .received(true)
                .duplicate(false)
                .processingStatus(WebhookProcessingStatus.PROCESSED)
                .message("Webhook processed successfully")
                .build();
    }

    private Optional<PaymentAttempt> resolveAttempt(ProviderWebhookEvent providerEvent) {
        if (providerEvent.getProviderSessionId() != null
                && !providerEvent.getProviderSessionId().isBlank()) {
            Optional<PaymentAttempt> bySession = paymentAttemptRepository
                    .findByProviderSessionId(providerEvent.getProviderSessionId());

            if (bySession.isPresent()) {
                return bySession;
            }
        }

        if (providerEvent.getProviderPaymentIntentId() != null
                && !providerEvent.getProviderPaymentIntentId().isBlank()) {
            Optional<PaymentAttempt> byIntent = paymentAttemptRepository
                    .findByProviderPaymentIntentId(providerEvent.getProviderPaymentIntentId());

            if (byIntent.isPresent()) {
                return byIntent;
            }
        }

        if (providerEvent.getProviderChargeId() != null
                && !providerEvent.getProviderChargeId().isBlank()) {
            return paymentAttemptRepository.findByProviderChargeId(providerEvent.getProviderChargeId());
        }

        return Optional.empty();
    }

    private void applyProviderState(
            ProviderWebhookEvent providerEvent,
            Payment payment,
            PaymentAttempt attempt
    ) {
        switch (providerEvent.getStatus()) {
            case SUCCESS -> {
                attempt.setStatus(PaymentAttemptStatus.SUCCESS);
                attempt.setProviderPaymentIntentId(providerEvent.getProviderPaymentIntentId());
                attempt.setProviderChargeId(providerEvent.getProviderChargeId());
                attempt.setFailureReason(null);

                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setFailureReason(null);
            }

            case FAILED -> {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attempt.setProviderPaymentIntentId(providerEvent.getProviderPaymentIntentId());
                attempt.setProviderChargeId(providerEvent.getProviderChargeId());
                attempt.setFailureReason(providerEvent.getFailureReason());

                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(providerEvent.getFailureReason());
            }

            case CANCELLED -> {
                attempt.setStatus(PaymentAttemptStatus.CANCELLED);
                attempt.setProviderPaymentIntentId(providerEvent.getProviderPaymentIntentId());
                attempt.setFailureReason(providerEvent.getFailureReason());

                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setFailureReason(providerEvent.getFailureReason());
            }

            case PROCESSING -> {
                attempt.setStatus(PaymentAttemptStatus.PROCESSING);
                attempt.setProviderPaymentIntentId(providerEvent.getProviderPaymentIntentId());
                attempt.setFailureReason(null);

                payment.setStatus(PaymentStatus.PROCESSING);
                payment.setFailureReason(null);
            }

            case IGNORED -> {
                // No payment state change.
            }
        }
    }

    private void markWebhookEvent(
            PaymentWebhookEvent webhookEvent,
            WebhookProcessingStatus processingStatus
    ) {
        webhookEvent.setProcessingStatus(processingStatus);
        webhookEvent.setProcessedAt(LocalDateTime.now());
        paymentWebhookEventRepository.save(webhookEvent);
    }
}