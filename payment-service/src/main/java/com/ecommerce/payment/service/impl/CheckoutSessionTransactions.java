package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.config.CheckoutUrlPolicy;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.dto.response.CreateCheckoutSessionResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutSessionTransactions {
    private static final List<PaymentAttemptStatus> ACTIVE = List.of(PaymentAttemptStatus.CREATED,
            PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION, PaymentAttemptStatus.PROCESSING);
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentGatewayFactory gateways;
    private final PaymentProviderProperties properties;
    private final CheckoutUrlPolicy urls;
    private final PaymentMetrics metrics;
    private final com.ecommerce.payment.repository.PaymentCancellationRequestRepository cancellations;
    private final com.ecommerce.payment.order.TrustedOrderClient trustedOrders;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID reserve(UUID orderId, UUID userId) {
        Payment payment = ownedPayablePayment(orderId, userId);
        var existing = attempts.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(payment.getId(), ACTIVE);
        if (existing.isPresent()) {
            requireUnexpired(existing.get());
            return existing.get().getId();
        }
        // All outcomes are terminal: expired/failed sessions cannot be silently replaced.
        if (attempts.findTopByPayment_IdOrderByCreatedAtDesc(payment.getId()).isPresent())
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
        gateways.getActiveGateway(); // Fail configuration checks before reserving work.
        payment.setProvider(properties.getProvider().getActive());
        String successUrl = returnUrl(properties.getCheckout().getSuccessUrl(), payment);
        String cancelUrl = returnUrl(properties.getCheckout().getCancelUrl(), payment);
        urls.validateReturnUrl(successUrl);
        urls.validateReturnUrl(cancelUrl);
        PaymentAttempt attempt = PaymentAttempt.builder().payment(payment).provider(payment.getProvider())
                .idempotencyKey("checkout:" + UUID.randomUUID())
                .status(PaymentAttemptStatus.CREATED)
                .successUrl(successUrl).cancelUrl(cancelUrl)
                .expiresAt(Instant.now().plus(31, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS))
                .build();
        return attempts.saveAndFlush(attempt).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateCheckoutSessionResponse complete(UUID orderId, UUID userId, UUID attemptId) {
        // The shared payment lock serializes all checkout, webhook, cancellation and expiry writers.
        Payment payment = ownedPayablePayment(orderId, userId);
        PaymentAttempt attempt = attempts.findById(attemptId)
                .filter(candidate -> candidate.getPayment().getId().equals(payment.getId()))
                .orElseThrow(() -> new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT));
        requireUnexpired(attempt);
        if (!ACTIVE.contains(attempt.getStatus()))
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
        if (attempt.getStatus() == PaymentAttemptStatus.PROCESSING)
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_PROCESSING);
        if (attempt.getCheckoutUrl() != null && attempt.getProviderSessionId() != null)
            return response(payment, attempt);
        // Legacy unmaterialized attempts cannot be recreated with guessed provider parameters.
        if (attempt.getSuccessUrl() == null || attempt.getCancelUrl() == null)
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
        var command = CreateCheckoutSessionCommand.builder().paymentId(payment.getId()).orderId(orderId)
                .userId(userId).amount(payment.getAmount()).currency(payment.getCurrency())
                .idempotencyKey(attempt.getIdempotencyKey()).successUrl(attempt.getSuccessUrl())
                .cancelUrl(attempt.getCancelUrl()).expiresAt(attempt.getExpiresAt()).build();
        var gateway = gateways.getGateway(attempt.getProvider());
        final com.ecommerce.payment.provider.model.CheckoutSessionResult result;
        try {
            result = metrics.recordProviderLatency(attempt.getProvider(), () -> gateway.createCheckoutSession(command));
        } catch (PaymentApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, exception);
        }
        if (result == null || result.getProvider() != attempt.getProvider()
                || result.getProviderSessionId() == null || result.getExpiresAt() == null)
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
        urls.validateCheckoutUrl(result.getProvider(), result.getCheckoutUrl());
        if (!result.getExpiresAt().isAfter(Instant.now()))
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_CHECKOUT_SESSION_EXPIRED);
        attempt.setProviderSessionId(result.getProviderSessionId());
        attempt.setProviderPaymentIntentId(result.getProviderPaymentIntentId());
        attempt.setProviderChargeId(result.getProviderChargeId());
        attempt.setCheckoutUrl(result.getCheckoutUrl());
        attempt.setExpiresAt(result.getExpiresAt());
        attempt.setStatus(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION);
        payment.setStatus(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        payment.setFailureReason(null);
        attempts.saveAndFlush(attempt);
        metrics.checkoutSessionCreated(result.getProvider());
        return response(payment, attempt);
    }

    private Payment ownedPayablePayment(UUID orderId, UUID userId) {
        Payment payment = payments.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentApiException(PaymentErrorCode.PAYMENT_PREPARING));
        if (!payment.getUserId().equals(userId)) throw new PaymentApiException(PaymentErrorCode.PAYMENT_NOT_OWNED);
        switch (payment.getStatus()) {
            case PENDING, REQUIRES_CUSTOMER_ACTION -> { }
            case PROCESSING -> throw new PaymentApiException(PaymentErrorCode.PAYMENT_PROCESSING);
            case EXPIRED -> throw new PaymentApiException(PaymentErrorCode.PAYMENT_EXPIRED);
            case CANCELLED -> throw new PaymentApiException(PaymentErrorCode.PAYMENT_CANCELLED);
            case SUCCESS -> throw new PaymentApiException(PaymentErrorCode.PAYMENT_ALREADY_COMPLETED);
            default -> throw new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
        }
        if (cancellations.existsByOrderId(orderId))
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_CANCELLED);
        try {
            trustedOrders.validatePayable(orderId, userId, payment.getAmount(), payment.getCurrency());
        } catch (com.ecommerce.common.exception.BadRequestException stale) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_STATE_CONFLICT);
        } catch (IllegalStateException unavailable) {
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, unavailable);
        }
        return payment;
    }

    private void requireUnexpired(PaymentAttempt attempt) {
        if (attempt.getExpiresAt() == null || !attempt.getExpiresAt().isAfter(Instant.now()))
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_CHECKOUT_SESSION_EXPIRED);
    }

    private String returnUrl(String template, Payment payment) {
        return template.replace("{ORDER_ID}", payment.getOrderId().toString())
                .replace("{PAYMENT_ID}", payment.getId().toString());
    }

    private CreateCheckoutSessionResponse response(Payment payment, PaymentAttempt attempt) {
        urls.validateCheckoutUrl(attempt.getProvider(), attempt.getCheckoutUrl());
        return CreateCheckoutSessionResponse.builder().paymentId(payment.getId()).orderId(payment.getOrderId())
                .status(payment.getStatus()).provider(attempt.getProvider()).checkoutUrl(attempt.getCheckoutUrl())
                .expiresAt(attempt.getExpiresAt()).build();
    }
}
