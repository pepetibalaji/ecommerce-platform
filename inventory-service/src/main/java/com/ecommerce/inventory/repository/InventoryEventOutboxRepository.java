package com.ecommerce.inventory.repository;
import com.ecommerce.inventory.entity.InventoryEventOutbox;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface InventoryEventOutboxRepository extends JpaRepository<InventoryEventOutbox, UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from InventoryEventOutbox e where (e.status='PENDING' and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)) or (e.status='PROCESSING' and e.leaseUntil <= :now) order by e.createdAt")
 List<InventoryEventOutbox> findDueForUpdate(@Param("now") Instant now, org.springframework.data.domain.Pageable page);
 long countByStatus(InventoryEventOutbox.Status status);
}
