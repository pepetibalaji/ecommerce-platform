package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<PaymentRefund> findByPayment_IdAndIdempotencyKey(
            UUID paymentId,
            String idempotencyKey
    );
    Optional<PaymentRefund> findByProviderRefundId(String providerRefundId);
}