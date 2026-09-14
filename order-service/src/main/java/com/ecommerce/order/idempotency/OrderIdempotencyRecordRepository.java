package com.ecommerce.order.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderIdempotencyRecordRepository extends JpaRepository<OrderIdempotencyRecord, UUID> {

    /**
     * Avoids a constraint exception. PostgreSQL waits for an in-flight conflicting insert, then
     * deterministically returns zero to the losing checkout node.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into order_idempotency_records
                (id, user_id, idempotency_key, request_hash, status, created_at, expires_at)
            values
                (:id, :userId, :idempotencyKey, :requestHash, 'PROCESSING', :createdAt, :expiresAt)
            on conflict (user_id, idempotency_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt
    );

    Optional<OrderIdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
            from OrderIdempotencyRecord record
            where record.userId = :userId
              and record.idempotencyKey = :idempotencyKey
            """)
    Optional<OrderIdempotencyRecord> findByUserIdAndIdempotencyKeyForUpdate(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
