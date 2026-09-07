package com.ecommerce.auth.repository;
import com.ecommerce.auth.entity.AuthOutboxEvent;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
public interface AuthOutboxEventRepository extends JpaRepository<AuthOutboxEvent, UUID> {
  /** Compatibility/read-only inspection helper; publishers must use {@link #claimable}. */
  List<AuthOutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(value = "select * from auth_outbox_events where published_at is null and dead_lettered_at is null "
      + "and (next_attempt_at is null or next_attempt_at <= :now) "
      + "and (lease_until is null or lease_until < :now) order by created_at asc for update skip locked limit :limit", nativeQuery = true)
  List<AuthOutboxEvent> claimable(@Param("now") Instant now, @Param("limit") int limit);

  long countByPublishedAtIsNullAndDeadLetteredAtIsNull();
  long countByDeadLetteredAtIsNotNull();
  List<AuthOutboxEvent> findTop100ByDeadLetteredAtIsNotNullOrderByCreatedAtAsc();
}
