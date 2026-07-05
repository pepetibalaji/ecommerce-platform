package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import com.ecommerce.payment.dto.response.CreateCheckoutSessionResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.provider.model.CheckoutSessionResult;
import com.ecommerce.payment.provider.model.CreateCheckoutSessionCommand;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class PaymentCheckoutServiceImpl implements PaymentCheckoutService {

    private static final List<PaymentAttemptStatus> ACTIVE_ATTEMPT_STATUSES =
            List.of(PaymentAttemptStatus.CREATED, PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION);

    private final PaymentRepository paymentRepository;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final PaymentGatewayFactory paymentGatewayFactory;

    @Override
    public CreateCheckoutSessionResponse createCheckoutSession(UUID orderId, UUID userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId
                ));

        validateOwnership(payment, userId);
        validatePaymentCanCreateCheckout(payment);

        LocalDateTime now = LocalDateTime.now();

        return paymentAttemptRepository
                .findTopByPayment_IdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        payment.getId(),
                        ACTIVE_ATTEMPT_STATUSES,
                        now
                )
                .map(activeAttempt -> toCheckoutResponse(payment, activeAttempt))
                .orElseGet(() -> createNewProviderCheckoutSession(payment));
    }

    private CreateCheckoutSessionResponse createNewProviderCheckoutSession(Payment payment) {
        PaymentGateway paymentGateway = paymentGatewayFactory.getActiveGateway();

        CreateCheckoutSessionCommand command = CreateCheckoutSessionCommand.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .idempotencyKey(payment.getIdempotencyKey() + ":checkout")
                .build();

        CheckoutSessionResult result = paymentGateway.createCheckoutSession(command);

        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(payment)
                .provider(result.getProvider())
                .providerSessionId(result.getProviderSessionId())
                .providerPaymentIntentId(result.getProviderPaymentIntentId())
                .providerChargeId(result.getProviderChargeId())
                .checkoutUrl(result.getCheckoutUrl())
                .status(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION)
                .expiresAt(result.getExpiresAt())
                .build();

        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);

        payment.setProvider(result.getProvider());
        payment.setStatus(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        payment.setFailureReason(null);
        paymentRepository.save(payment);

        return toCheckoutResponse(payment, savedAttempt);
    }

    private CreateCheckoutSessionResponse toCheckoutResponse(
            Payment payment,
            PaymentAttempt attempt
    ) {
        return CreateCheckoutSessionResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .checkoutUrl(attempt.getCheckoutUrl())
                .expiresAt(attempt.getExpiresAt())
                .build();
    }

    private void validateOwnership(Payment payment, UUID userId) {
        if (!payment.getUserId().equals(userId)) {
            throw new UnauthorizedException(
                    "User is not allowed to access payment for order: " + payment.getOrderId()
            );
        }
    }

    private void validatePaymentCanCreateCheckout(Payment payment) {
        if (PaymentStatus.SUCCESS == payment.getStatus()) {
            throw new BadRequestException(
                    "Payment is already successful for order: " + payment.getOrderId()
            );
        }

        if (PaymentStatus.PROCESSING == payment.getStatus()) {
            throw new BadRequestException(
                    "Payment is already processing for order: " + payment.getOrderId()
            );
        }

        if (PaymentStatus.REFUND_REQUESTED == payment.getStatus()
                || PaymentStatus.REFUND_PROCESSING == payment.getStatus()
                || PaymentStatus.REFUNDED == payment.getStatus()
                || PaymentStatus.REFUND_FAILED == payment.getStatus()) {
            throw new BadRequestException(
                    "Cannot create checkout session for refunded/refund-processing payment: "
                            + payment.getId()
            );
        }
    }
}