package com.ecommerce.order.repository;

import com.ecommerce.order.entity.InventoryReleaseOutbox;
import com.ecommerce.order.entity.InventoryReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    List<InventoryReleaseOutbox> lockNextPending(@Param("batchSize") int batchSize, @Param("now") Instant now);

    /** Compatibility bridge for callers compiled against the pre-UTC API. */
    default List<InventoryReleaseOutbox> lockNextPending(int batchSize, LocalDateTime now) {
        return lockNextPending(batchSize, now.toInstant(ZoneOffset.UTC));
    }

    long countByStatus(InventoryReleaseStatus status);
}
