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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
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

    @InjectMocks
    private InventoryReleaseOutboxProcessor inventoryReleaseOutboxProcessor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryReleaseOutboxProcessor, "batchSize", 25);
    }

    @Test
    void processPendingReleases_shouldCompleteCommandAfterInventoryAcknowledgesRelease() {
        InventoryReleaseOutbox command = pendingCommand(InventoryReleaseReason.PAYMENT_FAILED);
        when(inventoryReleaseOutboxRepository.lockNextPending(25)).thenReturn(List.of(command));

        inventoryReleaseOutboxProcessor.processPendingReleases();

        verify(inventoryGrpcClient).releaseStock(
                command.getProductId(), command.getQuantity(), command.getReservationId());
        verify(paymentOutcomeMetrics).inventoryReleaseSucceeded("payment_failed");
        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.COMPLETED);
        assertThat(command.getCompletedAt()).isNotNull();
        assertThat(command.getLastError()).isNull();
    }

    @Test
    void processPendingReleases_shouldRecordAndRetryFailedCommand() {
        InventoryReleaseOutbox command = pendingCommand(InventoryReleaseReason.CANCELLED);
        when(inventoryReleaseOutboxRepository.lockNextPending(25)).thenReturn(List.of(command));
        doThrow(new RuntimeException("inventory unavailable"))
                .when(inventoryGrpcClient)
                .releaseStock(command.getProductId(), command.getQuantity(), command.getReservationId());

        inventoryReleaseOutboxProcessor.processPendingReleases();

        verify(paymentOutcomeMetrics).inventoryReleaseFailed("cancelled");
        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.PENDING);
        assertThat(command.getAttemptCount()).isEqualTo(1);
        assertThat(command.getLastError()).isEqualTo("inventory unavailable");
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
