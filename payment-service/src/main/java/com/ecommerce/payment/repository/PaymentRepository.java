package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderId(UUID orderId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}