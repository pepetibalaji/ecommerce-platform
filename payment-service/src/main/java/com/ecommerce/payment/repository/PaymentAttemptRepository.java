package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentAttempt> findTopByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentAttempt> findByPayment_IdAndIdempotencyKey(
            UUID paymentId,
            String idempotencyKey
    );

    Optional<PaymentAttempt> findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(
            UUID paymentId,
            Collection<PaymentAttemptStatus> statuses
    );

    Optional<PaymentAttempt> findTopByPayment_IdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID paymentId,
            Collection<PaymentAttemptStatus> statuses,
            LocalDateTime now
    );

    Optional<PaymentAttempt> findByProviderAndProviderSessionId(
            PaymentProvider provider,
            String providerSessionId
    );

    Optional<PaymentAttempt> findByProviderAndProviderPaymentIntentId(
            PaymentProvider provider,
            String providerPaymentIntentId
    );

    Optional<PaymentAttempt> findByProviderAndProviderChargeId(
            PaymentProvider provider,
            String providerChargeId
    );

}