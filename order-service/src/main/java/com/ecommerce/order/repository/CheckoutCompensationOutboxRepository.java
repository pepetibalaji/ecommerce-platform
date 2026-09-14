package com.ecommerce.order.repository;
import com.ecommerce.order.entity.CheckoutCompensationOutbox;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant; import java.util.*;
public interface CheckoutCompensationOutboxRepository extends JpaRepository<CheckoutCompensationOutbox, UUID> {
 boolean existsByReservationId(UUID reservationId);
 @Query(value="select * from checkout_compensation_outbox where status='PENDING' and next_attempt_at <= :now order by next_attempt_at limit :batchSize for update skip locked", nativeQuery=true)
 List<CheckoutCompensationOutbox> lockPending(@Param("now") Instant now, @Param("batchSize") int batchSize);
 long countByStatus(String status);
}
