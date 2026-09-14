package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentCancellationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentCancellationRequestRepository extends JpaRepository<PaymentCancellationRequest, UUID> {
    boolean existsByOrderId(UUID orderId);

    @Query(value = """
            SELECT * FROM payment_cancellation_requests
            WHERE status IN ('REQUESTED', 'WAITING_PROVIDER') AND next_attempt_at <= :now
            ORDER BY next_attempt_at, received_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentCancellationRequest> findNextForUpdate(@Param("now") Instant now);
}
