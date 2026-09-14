package com.ecommerce.order.idempotency;

import com.ecommerce.order.api.OrderApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Mock
    private OrderIdempotencyRecordRepository repository;

    private OrderIdempotencyService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new OrderIdempotencyService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        userId = UUID.randomUUID();
    }

    @Test
    void claim_acquiresCommittedClaimBeforeCheckoutWork() {
        when(repository.insertIfAbsent(any(), eq(userId), eq("checkout-key"), eq("hash"), any(), any()))
                .thenReturn(1);

        OrderIdempotencyService.Claim result = service.claim(
                userId, "checkout-key", "hash", Duration.ofHours(24));

        assertThat(result.state()).isEqualTo(OrderIdempotencyService.State.ACQUIRED);
        assertThat(result.orderId()).isNull();

        ArgumentCaptor<Instant> createdAt = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> expiresAt = ArgumentCaptor.forClass(Instant.class);
        verify(repository).insertIfAbsent(any(), eq(userId), eq("checkout-key"), eq("hash"),
                createdAt.capture(), expiresAt.capture());
        assertThat(createdAt.getValue()).isEqualTo(NOW);
        assertThat(expiresAt.getValue()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void claim_returnsWinningCompletedOrderAfterConcurrentInsertConflict() {
        UUID winnerOrderId = UUID.randomUUID();
        OrderIdempotencyRecord winner = record("checkout-key", "hash", winnerOrderId);
        when(repository.insertIfAbsent(any(), eq(userId), eq("checkout-key"), eq("hash"), any(), any()))
                .thenReturn(0);
        when(repository.findByUserIdAndIdempotencyKey(userId, "checkout-key"))
                .thenReturn(Optional.of(winner));

        OrderIdempotencyService.Claim result = service.claim(
                userId, "checkout-key", "hash", Duration.ofHours(24));

        assertThat(result.state()).isEqualTo(OrderIdempotencyService.State.COMPLETED);
        assertThat(result.orderId()).isEqualTo(winnerOrderId);
    }

    @Test
    void claim_rejectsSameKeyWithDifferentPayloadAfterConcurrentConflict() {
        OrderIdempotencyRecord existing = record("checkout-key", "original-hash", null);
        when(repository.insertIfAbsent(any(), eq(userId), eq("checkout-key"), eq("changed-hash"), any(), any()))
                .thenReturn(0);
        when(repository.findByUserIdAndIdempotencyKey(userId, "checkout-key"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.claim(userId, "checkout-key", "changed-hash", Duration.ofHours(24)))
                .isInstanceOf(OrderApiException.class)
                .satisfies(error -> {
                    OrderApiException apiError = (OrderApiException) error;
                    assertThat(apiError.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                    assertThat(apiError.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void releaseIfIncomplete_deletesOnlyMatchingIncompleteClaim() {
        OrderIdempotencyRecord record = record("checkout-key", "hash", null);
        when(repository.findByUserIdAndIdempotencyKeyForUpdate(userId, "checkout-key"))
                .thenReturn(Optional.of(record));

        service.releaseIfIncomplete(userId, "checkout-key", "hash");

        verify(repository).delete(record);
    }

    @Test
    void releaseIfIncomplete_preservesCompletedOrDifferentClaim() {
        OrderIdempotencyRecord completed = record("checkout-key", "hash", UUID.randomUUID());
        when(repository.findByUserIdAndIdempotencyKeyForUpdate(userId, "checkout-key"))
                .thenReturn(Optional.of(completed));

        service.releaseIfIncomplete(userId, "checkout-key", "hash");

        verify(repository, never()).delete(any());
    }

    private OrderIdempotencyRecord record(String key, String requestHash, UUID orderId) {
        OrderIdempotencyRecord record = new OrderIdempotencyRecord();
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(record, "userId", userId);
        ReflectionTestUtils.setField(record, "idempotencyKey", key);
        ReflectionTestUtils.setField(record, "requestHash", requestHash);
        ReflectionTestUtils.setField(record, "orderId", orderId);
        ReflectionTestUtils.setField(record, "status", orderId == null ? "PROCESSING" : "COMPLETED");
        ReflectionTestUtils.setField(record, "createdAt", NOW);
        ReflectionTestUtils.setField(record, "expiresAt", NOW.plus(Duration.ofHours(24)));
        return record;
    }
}
