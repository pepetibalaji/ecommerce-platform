package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM payment_refunds
            WHERE status IN ('REFUND_REQUESTED', 'REFUND_PROCESSING')
              AND next_attempt_at <= :now AND (lease_until IS NULL OR lease_until <= :now)
            ORDER BY next_attempt_at, created_at, id
            LIMIT :batchSize FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentRefund> claimDue(@org.springframework.data.repository.query.Param("now") java.time.Instant now,
                                @org.springframework.data.repository.query.Param("batchSize") int batchSize);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from PaymentRefund r where r.id = :id")
    Optional<PaymentRefund> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<PaymentRefund> findByPayment_IdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<PaymentRefund> findByPayment_IdAndIdempotencyKey(
            UUID paymentId,
            String idempotencyKey
    );
    Optional<PaymentRefund> findByProviderRefundId(String providerRefundId);
    @org.springframework.data.jpa.repository.Query("select r.payment.orderId from PaymentRefund r where r.id = :refundId and r.payment.id = :paymentId")
    Optional<UUID> findOrderId(@org.springframework.data.repository.query.Param("paymentId") UUID paymentId,
                              @org.springframework.data.repository.query.Param("refundId") UUID refundId);
    @org.springframework.data.jpa.repository.Query("select p.orderId from Payment p where p.id = :paymentId")
    Optional<UUID> findPaymentOrderId(@org.springframework.data.repository.query.Param("paymentId") UUID paymentId);
}
