package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderRefundRequestOutbox;
import com.ecommerce.order.entity.RefundRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRefundRequestOutboxRepository extends JpaRepository<OrderRefundRequestOutbox, UUID> {

    Optional<OrderRefundRequestOutbox> findByOrderId(UUID orderId);

    @Query(value = """
            select * from order_refund_request_outbox
            where status = 'PENDING' and next_attempt_at <= :now
            order by next_attempt_at, created_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OrderRefundRequestOutbox> lockNextPending(@Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByStatus(RefundRequestStatus status);
}
