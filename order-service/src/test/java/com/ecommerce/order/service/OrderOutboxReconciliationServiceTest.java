package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderOutboxReconciliationResponse;
import com.ecommerce.order.entity.InventoryReleaseStatus;
import com.ecommerce.order.entity.RefundRequestStatus;
import com.ecommerce.order.repository.CheckoutCompensationOutboxRepository;
import com.ecommerce.order.repository.InventoryReleaseOutboxRepository;
import com.ecommerce.order.repository.OrderCreatedOutboxRepository;
import com.ecommerce.order.repository.OrderRefundRequestOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOutboxReconciliationServiceTest {

    @Mock
    private OrderCreatedOutboxRepository orderCreatedOutboxRepository;
    @Mock
    private InventoryReleaseOutboxRepository inventoryReleaseOutboxRepository;
    @Mock
    private CheckoutCompensationOutboxRepository checkoutCompensationOutboxRepository;
    @Mock
    private OrderRefundRequestOutboxRepository refundRequestOutboxRepository;

    @Test
    void snapshot_exposesPendingAndTerminalWorkForEveryDurableHandoff() {
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        when(orderCreatedOutboxRepository.countByStatus("PENDING")).thenReturn(2L);
        when(orderCreatedOutboxRepository.countByStatus("PUBLISHED")).thenReturn(8L);
        when(orderCreatedOutboxRepository.countByStatus("FAILED")).thenReturn(1L);

        when(inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.PENDING)).thenReturn(3L);
        when(inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.COMPLETED)).thenReturn(7L);
        when(inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.FAILED)).thenReturn(1L);
        when(inventoryReleaseOutboxRepository.countByStatus(InventoryReleaseStatus.MANUAL_REVIEW)).thenReturn(4L);

        when(checkoutCompensationOutboxRepository.countByStatus("PENDING")).thenReturn(5L);
        when(checkoutCompensationOutboxRepository.countByStatus("COMPLETED")).thenReturn(6L);
        when(checkoutCompensationOutboxRepository.countByStatus("FAILED")).thenReturn(2L);

        when(refundRequestOutboxRepository.countByStatus(RefundRequestStatus.PENDING)).thenReturn(9L);
        when(refundRequestOutboxRepository.countByStatus(RefundRequestStatus.PUBLISHED)).thenReturn(10L);
        when(refundRequestOutboxRepository.countByStatus(RefundRequestStatus.FAILED)).thenReturn(3L);

        OrderOutboxReconciliationService service = new OrderOutboxReconciliationService(
                orderCreatedOutboxRepository,
                inventoryReleaseOutboxRepository,
                checkoutCompensationOutboxRepository,
                refundRequestOutboxRepository,
                Clock.fixed(now, ZoneOffset.UTC));

        OrderOutboxReconciliationResponse snapshot = service.snapshot();

        assertThat(snapshot.observedAt()).isEqualTo(now);
        assertThat(snapshot.orderCreated().pending()).isEqualTo(2);
        assertThat(snapshot.orderCreated().published()).isEqualTo(8);
        assertThat(snapshot.orderCreated().failed()).isEqualTo(1);
        assertThat(snapshot.inventoryRelease().manualReview()).isEqualTo(4);
        assertThat(snapshot.checkoutCompensation().pending()).isEqualTo(5);
        assertThat(snapshot.checkoutCompensation().failed()).isEqualTo(2);
        assertThat(snapshot.refundRequest().pending()).isEqualTo(9);
        assertThat(snapshot.refundRequest().published()).isEqualTo(10);
        assertThat(snapshot.refundRequest().failed()).isEqualTo(3);
    }
}
