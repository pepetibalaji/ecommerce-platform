package com.ecommerce.order.service;

import com.ecommerce.order.entity.InventoryReleaseOutbox;
import com.ecommerce.order.entity.InventoryReleaseReason;
import com.ecommerce.order.entity.InventoryReleaseStatus;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import com.ecommerce.order.repository.InventoryReleaseOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReleaseOutboxServiceTest {

    @Mock
    private InventoryReleaseOutboxRepository inventoryReleaseOutboxRepository;

    @Mock
    private PaymentOutcomeMetrics paymentOutcomeMetrics;

    @InjectMocks
    private InventoryReleaseOutboxService inventoryReleaseOutboxService;

    @Test
    void enqueueFor_shouldPersistOnePendingCommandForEachReservation() {
        Order order = orderWithItem();
        OrderItem item = order.getItems().getFirst();

        when(inventoryReleaseOutboxRepository.existsByReservationId(item.getInventoryReservationId()))
                .thenReturn(false);

        inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.PAYMENT_FAILED);

        ArgumentCaptor<InventoryReleaseOutbox> commandCaptor = ArgumentCaptor.forClass(InventoryReleaseOutbox.class);
        verify(inventoryReleaseOutboxRepository).save(commandCaptor.capture());

        InventoryReleaseOutbox command = commandCaptor.getValue();
        assertThat(command.getOrderId()).isEqualTo(order.getId());
        assertThat(command.getOrderItemId()).isEqualTo(item.getId());
        assertThat(command.getReservationId()).isEqualTo(item.getInventoryReservationId());
        assertThat(command.getReason()).isEqualTo(InventoryReleaseReason.PAYMENT_FAILED);
        assertThat(command.getStatus()).isEqualTo(InventoryReleaseStatus.PENDING);

        verify(paymentOutcomeMetrics).inventoryReleaseQueued("payment_failed");
    }

    @Test
    void enqueueFor_shouldNotCreateDuplicateCommandForAnExistingReservation() {
        Order order = orderWithItem();
        UUID reservationId = order.getItems().getFirst().getInventoryReservationId();
        when(inventoryReleaseOutboxRepository.existsByReservationId(reservationId)).thenReturn(true);

        inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.CANCELLED);

        verify(inventoryReleaseOutboxRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(paymentOutcomeMetrics);
    }

    @Test
    void enqueueFor_shouldFailBeforePersistingWhenAnOrderItemHasNoReservationId() {
        Order order = orderWithItem();
        order.getItems().getFirst().setInventoryReservationId(null);

        assertThrows(IllegalStateException.class,
                () -> inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.PAYMENT_FAILED));

        verifyNoInteractions(inventoryReleaseOutboxRepository, paymentOutcomeMetrics);
    }

    private Order orderWithItem() {
        Order order = new Order();
        order.setId(UUID.randomUUID());

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(UUID.randomUUID());
        item.setInventoryReservationId(UUID.randomUUID());
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));
        order.getItems().add(item);

        return order;
    }
}
