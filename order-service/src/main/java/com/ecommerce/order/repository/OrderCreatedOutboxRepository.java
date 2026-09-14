package com.ecommerce.order.repository;

import com.ecommerce.order.entity.OrderCreatedOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderCreatedOutboxRepository extends JpaRepository<OrderCreatedOutbox, UUID> {
    @Query(value = "select * from order_created_outbox where status = 'PENDING' and next_attempt_at <= :now order by next_attempt_at, created_at limit :batchSize for update skip locked", nativeQuery = true)
    List<OrderCreatedOutbox> lockNextPending(@Param("batchSize") int batchSize, @Param("now") Instant now);

    long countByStatus(String status);
}
