package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.dto.response.ProviderRefundStatus;
import com.ecommerce.payment.dto.response.WebhookAckResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.entity.PaymentRefund;
import com.ecommerce.payment.entity.PaymentWebhookEvent;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.enums.RefundStatus;
import com.ecommerce.payment.enums.WebhookProcessingStatus;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;
import com.ecommerce.payment.provider.model.ProviderWebhookEvent;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRefundRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.repository.PaymentWebhookEventRepository;
import com.ecommerce.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private static final String STRIPE_CHECKOUT_SESSION_EXPIRED =
            "checkout.session.expired";

    private final PaymentGatewayFactory paymentGatewayFactory;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMetrics paymentMetrics;

    @Override
    public WebhookAckResponse processWebhook(
            PaymentProvider provider,
            String payload,
            String signature
    ) {
        PaymentGateway gateway = paymentGatewayFactory.getGateway(provider);

        ProviderWebhookEvent providerEvent;
        try {
            providerEvent = paymentMetrics.recordProviderLatency(
                    provider,
                    () -> gateway.parseWebhookEvent(payload, signature)
            );
        } catch (RuntimeException exception) {
            paymentMetrics.webhookInvalidSignature(provider);
            throw exception;
        }

        PaymentProvider eventProvider = resolveProvider(provider, providerEvent);
        paymentMetrics.webhookReceived(eventProvider);

        boolean refundEvent = isRefundEvent(providerEvent);

        Optional<PaymentRefund> refundOptional = Optional.empty();
        Optional<PaymentAttempt> attemptOptional = Optional.empty();

        Payment payment = null;
        UUID paymentId = null;

        if (refundEvent) {
            refundOptional = resolveRefund(providerEvent);

            if (refundOptional.isPresent()) {
                payment = refundOptional.get().getPayment();
                paymentId = payment.getId();
            }
        } else {
            attemptOptional = resolveAttempt(eventProvider, providerEvent);

            if (attemptOptional.isPresent()) {
                payment = attemptOptional.get().getPayment();
                paymentId = payment.getId();
            }
        }

        int insertedRows = paymentWebhookEventRepository.insertReceivedEventIfAbsent(
                UUID.randomUUID(),
                eventProvider.name(),
                providerEvent.getProviderEventId(),
                paymentId,
                providerEvent.getEventType(),
                WebhookProcessingStatus.RECEIVED.name(),
                null,
                LocalDateTime.now(),
                null
        );

        if (insertedRows == 0) {
            paymentMetrics.webhookDuplicate(eventProvider);
            log.info(
                    "Duplicate webhook ignored. provider={}, providerEventId={}, eventType={}",
                    eventProvider,
                    providerEvent.getProviderEventId(),
                    providerEvent.getEventType()
            );

            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(true)
                    .processingStatus(WebhookProcessingStatus.IGNORED)
                    .message("Duplicate webhook ignored")
                    .build();
        }

        PaymentWebhookEvent savedWebhookEvent = paymentWebhookEventRepository
                .findByProviderAndProviderEventId(
                        eventProvider,
                        providerEvent.getProviderEventId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook event was inserted but could not be loaded. provider="
                                + eventProvider
                                + ", providerEventId="
                                + providerEvent.getProviderEventId()
                ));

        if (ProviderPaymentStatus.IGNORED == providerEvent.getStatus()
                && !refundEvent) {
            markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.IGNORED);

            log.info(
                    "Webhook event ignored. provider={}, providerEventId={}, eventType={}",
                    eventProvider,
                    providerEvent.getProviderEventId(),
                    providerEvent.getEventType()
            );

            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(false)
                    .processingStatus(WebhookProcessingStatus.IGNORED)
                    .message("Webhook event ignored")
                    .build();
        }

        if (refundEvent) {
            if (refundOptional.isEmpty()) {
                markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.FAILED);

                log.warn(
                        "Refund webhook could not be resolved. provider={}, providerEventId={}, eventType={}, providerRefundId={}, providerPaymentIntentId={}, providerChargeId={}",
                        eventProvider,
                        providerEvent.getProviderEventId(),
                        providerEvent.getEventType(),
                        providerEvent.getProviderRefundId(),
                        providerEvent.getProviderPaymentIntentId(),
                        providerEvent.getProviderChargeId()
                );

                return WebhookAckResponse.builder()
                        .received(true)
                        .duplicate(false)
                        .processingStatus(WebhookProcessingStatus.FAILED)
                        .message("Refund could not be resolved")
                        .build();
            }

            PaymentRefund refund = refundOptional.get();
            payment = refund.getPayment();

            applyRefundState(providerEvent, payment, refund);

            paymentRefundRepository.save(refund);
            paymentRepository.save(payment);

            if (refund.getStatus() == RefundStatus.REFUNDED) {
                BigDecimal totalRefunded = paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(payment.getId())
                        .stream().filter(candidate -> candidate.getStatus() == RefundStatus.REFUNDED)
                        .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                paymentEventPublisher.publishRefundCompleted(payment, refund, totalRefunded);
            }

            markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.PROCESSED);

            log.info(
                    "Refund webhook processed successfully. provider={}, providerEventId={}, eventType={}, paymentId={}, orderId={}, refundId={}, refundStatus={}, paymentStatus={}",
                    eventProvider,
                    providerEvent.getProviderEventId(),
                    providerEvent.getEventType(),
                    payment.getId(),
                    payment.getOrderId(),
                    refund.getId(),
                    refund.getStatus(),
                    payment.getStatus()
            );

            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(false)
                    .processingStatus(WebhookProcessingStatus.PROCESSED)
                    .message("Refund webhook processed successfully")
                    .build();
        }

        if (attemptOptional.isEmpty()) {
            markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.FAILED);

            log.warn(
                    "Payment attempt could not be resolved for webhook. provider={}, providerEventId={}, eventType={}, providerSessionId={}, providerPaymentIntentId={}, providerChargeId={}",
                    eventProvider,
                    providerEvent.getProviderEventId(),
                    providerEvent.getEventType(),
                    providerEvent.getProviderSessionId(),
                    providerEvent.getProviderPaymentIntentId(),
                    providerEvent.getProviderChargeId()
            );

            return WebhookAckResponse.builder()
                    .received(true)
                    .duplicate(false)
                    .processingStatus(WebhookProcessingStatus.FAILED)
                    .message("Payment attempt could not be resolved")
                    .build();
        }

        PaymentAttempt attempt = attemptOptional.get();
        payment = attempt.getPayment();

        applyProviderState(providerEvent, payment, attempt);

        paymentAttemptRepository.save(attempt);
        paymentRepository.save(payment);

        markWebhookEvent(savedWebhookEvent, WebhookProcessingStatus.PROCESSED);

        publishOutcomeIfTerminal(payment);

        log.info(
                "Webhook processed successfully. provider={}, providerEventId={}, eventType={}, paymentId={}, orderId={}, paymentStatus={}, attemptStatus={}",
                eventProvider,
                providerEvent.getProviderEventId(),
                providerEvent.getEventType(),
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                attempt.getStatus()
        );

        return WebhookAckResponse.builder()
                .received(true)
                .duplicate(false)
                .processingStatus(WebhookProcessingStatus.PROCESSED)
                .message("Webhook processed successfully")
                .build();
    }

    private PaymentProvider resolveProvider(
            PaymentProvider fallbackProvider,
            ProviderWebhookEvent providerEvent
    ) {
        if (providerEvent.getProvider() != null) {
            return providerEvent.getProvider();
        }

        return fallbackProvider;
    }

    private Optional<PaymentAttempt> resolveAttempt(
            PaymentProvider provider,
            ProviderWebhookEvent providerEvent
    ) {
        if (hasText(providerEvent.getProviderSessionId())) {
            Optional<PaymentAttempt> bySession = paymentAttemptRepository
                    .findByProviderAndProviderSessionId(
                            provider,
                            providerEvent.getProviderSessionId()
                    );

            if (bySession.isPresent()) {
                return bySession;
            }
        }

        if (hasText(providerEvent.getProviderPaymentIntentId())) {
            Optional<PaymentAttempt> byIntent = paymentAttemptRepository
                    .findByProviderAndProviderPaymentIntentId(
                            provider,
                            providerEvent.getProviderPaymentIntentId()
                    );

            if (byIntent.isPresent()) {
                return byIntent;
            }
        }

        if (hasText(providerEvent.getProviderChargeId())) {
            return paymentAttemptRepository.findByProviderAndProviderChargeId(
                    provider,
                    providerEvent.getProviderChargeId()
            );
        }

        return Optional.empty();
    }

    private Optional<PaymentRefund> resolveRefund(ProviderWebhookEvent providerEvent) {
        if (!hasText(providerEvent.getProviderRefundId())) {
            return Optional.empty();
        }

        return paymentRefundRepository.findByProviderRefundId(
                providerEvent.getProviderRefundId()
        );
    }

    private boolean isRefundEvent(ProviderWebhookEvent providerEvent) {
        return providerEvent.getRefundStatus() != null
                || hasText(providerEvent.getProviderRefundId())
                || isRefundEventType(providerEvent.getEventType());
    }

    private boolean isRefundEventType(String eventType) {
        if (!hasText(eventType)) {
            return false;
        }

        return "refund.created".equals(eventType)
                || "refund.updated".equals(eventType)
                || "charge.refunded".equals(eventType)
                || "charge.refund.updated".equals(eventType);
    }

    private void applyProviderState(
            ProviderWebhookEvent providerEvent,
            Payment payment,
            PaymentAttempt attempt
    ) {
        applyProviderIdentifiers(providerEvent, attempt);

        switch (providerEvent.getStatus()) {
            case SUCCESS -> {
                attempt.setStatus(PaymentAttemptStatus.SUCCESS);
                attempt.setFailureReason(null);

                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setFailureReason(null);
            }

            case FAILED -> {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attempt.setFailureReason(providerEvent.getFailureReason());

                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(providerEvent.getFailureReason());
            }

            case CANCELLED -> {
                attempt.setStatus(resolveCancelledAttemptStatus(providerEvent));
                attempt.setFailureReason(providerEvent.getFailureReason());

                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setFailureReason(providerEvent.getFailureReason());
            }

            case PROCESSING -> {
                attempt.setStatus(PaymentAttemptStatus.PROCESSING);
                attempt.setFailureReason(null);

                payment.setStatus(PaymentStatus.PROCESSING);
                payment.setFailureReason(null);
            }

            case IGNORED -> {
                // No payment state change.
            }
        }
    }

    private void applyRefundState(
            ProviderWebhookEvent providerEvent,
            Payment payment,
            PaymentRefund refund
    ) {
        if (hasText(providerEvent.getProviderRefundId())) {
            refund.setProviderRefundId(providerEvent.getProviderRefundId());
        }

        if (hasText(providerEvent.getFailureReason())) {
            refund.setFailureReason(providerEvent.getFailureReason());
        }

        ProviderRefundStatus refundStatus = providerEvent.getRefundStatus();

        if (refundStatus == null) {
            refundStatus = ProviderRefundStatus.PROCESSING;
        }

        switch (refundStatus) {
            case PROCESSING -> {
                refund.setStatus(RefundStatus.REFUND_PROCESSING);
                payment.setStatus(PaymentStatus.REFUND_PROCESSING);
                payment.setFailureReason(null);
            }

            case SUCCESS -> {
                refund.setStatus(RefundStatus.REFUNDED);
                BigDecimal priorRefunded = paymentRefundRepository.findByPayment_IdOrderByCreatedAtDesc(payment.getId())
                        .stream().filter(candidate -> !candidate.getId().equals(refund.getId())
                                && candidate.getStatus() == RefundStatus.REFUNDED)
                        .map(PaymentRefund::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                payment.setStatus(priorRefunded.add(refund.getAmount()).compareTo(payment.getAmount()) == 0
                        ? PaymentStatus.REFUNDED : PaymentStatus.SUCCESS);
                payment.setFailureReason(null);
            }

            case FAILED -> {
                refund.setStatus(RefundStatus.REFUND_FAILED);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setFailureReason(null);
            }

            case IGNORED -> {
                // No state change.
            }
        }
    }

    private void applyProviderIdentifiers(
            ProviderWebhookEvent providerEvent,
            PaymentAttempt attempt
    ) {
        if (hasText(providerEvent.getProviderSessionId())) {
            attempt.setProviderSessionId(providerEvent.getProviderSessionId());
        }

        if (hasText(providerEvent.getProviderPaymentIntentId())) {
            attempt.setProviderPaymentIntentId(providerEvent.getProviderPaymentIntentId());
        }

        if (hasText(providerEvent.getProviderChargeId())) {
            attempt.setProviderChargeId(providerEvent.getProviderChargeId());
        }
    }

    private PaymentAttemptStatus resolveCancelledAttemptStatus(
            ProviderWebhookEvent providerEvent
    ) {
        if (STRIPE_CHECKOUT_SESSION_EXPIRED.equals(providerEvent.getEventType())) {
            return PaymentAttemptStatus.EXPIRED;
        }

        return PaymentAttemptStatus.CANCELLED;
    }

    private void publishOutcomeIfTerminal(Payment payment) {
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            paymentMetrics.paymentSucceeded(payment.getProvider());
            paymentEventPublisher.publishPaymentSuccess(payment);
            return;
        }

        if (payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED) {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                paymentMetrics.paymentFailed(payment.getProvider());
            } else {
                paymentMetrics.paymentCancelled(payment.getProvider());
            }
            paymentEventPublisher.publishPaymentFailed(payment);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
