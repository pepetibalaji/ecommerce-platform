package com.ecommerce.order.service;

import com.ecommerce.order.entity.InventoryReleaseOutbox;
import com.ecommerce.order.entity.InventoryReleaseReason;
import com.ecommerce.order.entity.InventoryReleaseStatus;
import com.ecommerce.order.grpc.InventoryGrpcClient;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import com.ecommerce.order.repository.InventoryReleaseOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReleaseOutboxProcessorTest {

    @Mock
    private InventoryReleaseOutboxRepository inventoryReleaseOutboxRepository;

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private PaymentOutcomeMetrics paymentOutcomeMetrics;

    @Mock
    private InventoryReleaseRetryPolicy retryPolicy;

    @Mock
    private Clock clock;

    @InjectMocks
    private InventoryReleaseOutboxProcessor inventoryReleaseOutboxProcessor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryReleaseOutboxProcessor, "batchSize", 25);
        ReflectionTestUtils.setField(inventoryReleaseOutboxProcessor, "maxAttempts", 8);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-27T10:00:00Z"));
    }

    @Test
    void processPendingReleases_shouldCompleteCommandAfterInventoryAcknowledgesRelease() {
        InventoryReleaseOutbox command = pendingCommand(InventoryReleaseReason.PAYMENT_FAILED);
        when(inventoryReleaseOutboxRepository.lockNextPending(25, LocalDateTime.of(2026, 8, 27, 10, 0)))
                .thenReturn(List.of(command));

        inventoryReleaseOutboxProcessor.processPendingReleases();

        verify(inventoryGrpcClient).releaseStock(
                command.getProductId(), command.getQuantity(), command.getReservationId());
        verify(paymentOutcomeMetrics).inventoryReleaseSucceeded("payment_failed");
        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.COMPLETED);
        assertThat(command.getCompletedAt()).isNotNull();
        assertThat(command.getLastError()).isNull();
    }

    @Test
    void processPendingReleases_shouldRetryFailedFullRefundRelease() {
        InventoryReleaseOutbox command = pendingCommand(InventoryReleaseReason.FULL_REFUND);
        when(inventoryReleaseOutboxRepository.lockNextPending(25, LocalDateTime.of(2026, 8, 27, 10, 0)))
                .thenReturn(List.of(command));
        when(retryPolicy.delayForAttempt(1)).thenReturn(Duration.ofSeconds(2));
        doThrow(new RuntimeException("inventory unavailable"))
                .when(inventoryGrpcClient)
                .releaseStock(command.getProductId(), command.getQuantity(), command.getReservationId());

        inventoryReleaseOutboxProcessor.processPendingReleases();

        verify(paymentOutcomeMetrics).inventoryReleaseFailed("full_refund");
        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.PENDING);
        assertThat(command.getAttemptCount()).isEqualTo(1);
        assertThat(command.getLastError()).isEqualTo("inventory unavailable");
        assertThat(command.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 8, 27, 10, 0, 2));
    }

    @Test
    void processPendingReleases_shouldMarkCommandFailedWhenMaximumAttemptsAreReached() {
        InventoryReleaseOutbox command = pendingCommand(InventoryReleaseReason.CANCELLED);
        when(inventoryReleaseOutboxRepository.lockNextPending(25, LocalDateTime.of(2026, 8, 27, 10, 0)))
                .thenReturn(List.of(command));
        ReflectionTestUtils.setField(inventoryReleaseOutboxProcessor, "maxAttempts", 1);
        when(retryPolicy.delayForAttempt(1)).thenReturn(Duration.ofSeconds(1));
        doThrow(new RuntimeException("inventory unavailable"))
                .when(inventoryGrpcClient)
                .releaseStock(command.getProductId(), command.getQuantity(), command.getReservationId());

        inventoryReleaseOutboxProcessor.processPendingReleases();

        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.FAILED);
        assertThat(command.getAttemptCount()).isEqualTo(1);
        verify(paymentOutcomeMetrics).inventoryReleaseTerminalFailure("cancelled");
    }

    @Test
    void processPendingReleases_shouldRouteDeductedReservationToManualReviewWithoutRetry() {
        InventoryReleaseOutbox command = pendingCommand(InventoryReleaseReason.FULL_REFUND);
        when(inventoryReleaseOutboxRepository.lockNextPending(25, LocalDateTime.of(2026, 8, 27, 10, 0)))
                .thenReturn(List.of(command));
        doThrow(new RuntimeException("Deducted inventory reservations cannot be released"))
                .when(inventoryGrpcClient)
                .releaseStock(command.getProductId(), command.getQuantity(), command.getReservationId());

        inventoryReleaseOutboxProcessor.processPendingReleases();

        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.MANUAL_REVIEW);
        assertThat(command.getAttemptCount()).isZero();
        verify(paymentOutcomeMetrics, never()).inventoryReleaseFailed("full_refund");
    }

    @Test
    void processPendingReleases_shouldKeepConcurrentWorkersOnTheirLockedCommands() {
        InventoryReleaseOutbox firstCommand = pendingCommand(InventoryReleaseReason.CANCELLED);
        InventoryReleaseOutbox secondCommand = pendingCommand(InventoryReleaseReason.PAYMENT_FAILED);
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 10, 0);
        when(inventoryReleaseOutboxRepository.lockNextPending(25, now))
                .thenReturn(List.of(firstCommand), List.of(secondCommand));

        inventoryReleaseOutboxProcessor.processPendingReleases();
        inventoryReleaseOutboxProcessor.processPendingReleases();

        verify(inventoryGrpcClient).releaseStock(
                firstCommand.getProductId(), firstCommand.getQuantity(), firstCommand.getReservationId());
        verify(inventoryGrpcClient).releaseStock(
                secondCommand.getProductId(), secondCommand.getQuantity(), secondCommand.getReservationId());
        verify(inventoryGrpcClient, never()).releaseStock(
                firstCommand.getProductId(), secondCommand.getQuantity(), secondCommand.getReservationId());
    }

    private InventoryReleaseOutbox pendingCommand(InventoryReleaseReason reason) {
        return new InventoryReleaseOutbox(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                reason
        );
    }
}
