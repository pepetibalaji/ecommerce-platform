package com.ecommerce.order.repository;

import com.ecommerce.order.entity.InventoryReleaseOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

public interface InventoryReleaseOutboxRepository extends JpaRepository<InventoryReleaseOutbox, UUID> {

    boolean existsByReservationId(UUID reservationId);

    @Query(value = """
            select *
            from order_inventory_release_outbox
            where status = 'PENDING'
              and next_attempt_at <= :now
            order by next_attempt_at, created_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<InventoryReleaseOutbox> lockNextPending(@Param("batchSize") int batchSize, @Param("now") LocalDateTime now);
}
