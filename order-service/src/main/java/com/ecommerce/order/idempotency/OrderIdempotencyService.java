package com.ecommerce.order.idempotency;

import com.ecommerce.order.api.OrderApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private final OrderIdempotencyRecordRepository repository;
    private final Clock clock;

    /** Creates the short, committed claim that prevents concurrent checkouts from reserving twice. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(UUID userId, String idempotencyKey, String requestHash, Duration retention) {
        Instant now = Instant.now(clock);
        int inserted = repository.insertIfAbsent(
                UUID.randomUUID(), userId, idempotencyKey, requestHash, now, now.plus(retention));

        if (inserted == 1) {
            return Claim.acquired();
        }

        OrderIdempotencyRecord existing = repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared after a unique-key conflict"));
        ensureSameRequest(existing, requestHash);
        return existing.getOrderId() == null ? Claim.processing() : Claim.completed(existing.getOrderId());
    }

    /** Must join the checkout transaction; it serializes all same-key work before inventory calls. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderIdempotencyRecord lock(UUID userId, String idempotencyKey, String requestHash) {
        OrderIdempotencyRecord record = repository.findByUserIdAndIdempotencyKeyForUpdate(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim was not found"));
        ensureSameRequest(record, requestHash);
        return record;
    }

    /** Used after the checkout transaction has rolled back. It permits a retry after a failed checkout. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseIfIncomplete(UUID userId, String idempotencyKey, String requestHash) {
        repository.findByUserIdAndIdempotencyKeyForUpdate(userId, idempotencyKey)
                .filter(record -> record.getOrderId() == null && requestHash.equals(record.getRequestHash()))
                .ifPresent(repository::delete);
    }

    /** Completes a claim created by a rolling deployment that raced with the legacy orders index. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeExisting(UUID userId, String idempotencyKey, String requestHash, UUID orderId) {
        OrderIdempotencyRecord record = repository.findByUserIdAndIdempotencyKeyForUpdate(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim was not found"));
        ensureSameRequest(record, requestHash);
        record.complete(orderId);
    }

    private void ensureSameRequest(OrderIdempotencyRecord record, String requestHash) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new OrderApiException(
                    "IDEMPOTENCY_KEY_REUSED",
                    HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different checkout request.",
                    false,
                    List.of());
        }
    }

    public record Claim(State state, UUID orderId) {
        public static Claim acquired() {
            return new Claim(State.ACQUIRED, null);
        }

        public static Claim processing() {
            return new Claim(State.PROCESSING, null);
        }

        public static Claim completed(UUID orderId) {
            return new Claim(State.COMPLETED, orderId);
        }
    }

    public enum State { ACQUIRED, PROCESSING, COMPLETED }
}
