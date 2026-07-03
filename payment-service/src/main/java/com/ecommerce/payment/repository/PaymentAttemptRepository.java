package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentAttempt> findTopByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentAttempt> findByProviderSessionId(String providerSessionId);

    Optional<PaymentAttempt> findByProviderPaymentIntentId(String providerPaymentIntentId);

    Optional<PaymentAttempt> findByProviderChargeId(String providerChargeId);
}