package com.ecommerce.order.service;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderRefundRequestOutbox;
import com.ecommerce.order.entity.RefundRequestStatus;
import com.ecommerce.order.repository.OrderRefundRequestOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
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
class OrderRefundRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Mock
    private OrderRefundRequestOutboxRepository repository;
    @Mock
    private OrderLifecycleAuditService auditService;

    private OrderRefundRequestService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderRefundRequestService(repository, auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setPaymentId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setTotalAmount(new BigDecimal("499.00"));
        order.setCurrency("INR");
    }

    @Test
    void enqueueFullRefund_createsAuditedDurableCommandOnce() {
        UUID actorId = UUID.randomUUID();
        when(repository.findByOrderIdAndCommandType(order.getId(), "REFUND")).thenReturn(Optional.empty());
        when(repository.save(any(OrderRefundRequestOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRefundRequestOutbox outbox = service.enqueueFullRefund(
                order, actorId, "CUSTOMER", "Changed my mind");

        assertThat(outbox.getOrderId()).isEqualTo(order.getId());
        assertThat(outbox.getPaymentId()).isEqualTo(order.getPaymentId());
        assertThat(outbox.getStatus()).isEqualTo(RefundRequestStatus.PENDING);
        assertThat(outbox.getAmount()).isEqualByComparingTo("499.00");
        assertThat(outbox.getNextAttemptAt()).isEqualTo(NOW);
        verify(auditService).record(order.getId(), "REFUND_REQUESTED", actorId,
                "CUSTOMER", "Changed my mind", outbox.getId());
    }

    @Test
    void enqueueFullRefund_returnsExistingCommandWithoutDuplicateAudit() {
        OrderRefundRequestOutbox existing = new OrderRefundRequestOutbox(
                order.getId(), order.getPaymentId(), order.getUserId(), UUID.randomUUID(), "CUSTOMER",
                order.getTotalAmount(), order.getCurrency(), "Initial reason", NOW);
        when(repository.findByOrderIdAndCommandType(order.getId(), "REFUND")).thenReturn(Optional.of(existing));

        OrderRefundRequestOutbox result = service.enqueueFullRefund(
                order, UUID.randomUUID(), "CUSTOMER", "Retry after timeout");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void enqueueFullRefund_rejectsConfirmedOrderWithoutPaymentReference() {
        order.setPaymentId(null);
        when(repository.findByOrderIdAndCommandType(order.getId(), "REFUND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueueFullRefund(
                order, UUID.randomUUID(), "CUSTOMER", "Reason"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no payment id");

        verify(repository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }
    @Test
    void cancellationBeforePreparationKeepsAuditWithoutRequiringPaymentId() {
        order.setPaymentId(null);
        UUID actor = order.getUserId();
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var command = service.enqueueCancellation(order, actor, "CUSTOMER", "Cancel before redirect", false);
        assertThat(command.getCommandType()).isEqualTo("CANCELLATION");
        assertThat(command.getPaymentId()).isNull();
        assertThat(command.getRequestedBy()).isEqualTo(actor);
        assertThat(command.getCreatedAt()).isEqualTo(NOW);
        assertThat(command.getAmount()).isEqualByComparingTo(order.getTotalAmount());
        verify(auditService).record(order.getId(), "CANCELLATION_REQUESTED", actor, "CUSTOMER",
                "Cancel before redirect", command.getId());
    }

    @Test
    void expiryAndCustomerCancellationAreSeparateIdempotentCommands() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var expiry = service.enqueueCancellation(order, null, "ORDER_SYSTEM", "Payment window expired", true);
        when(repository.findByOrderIdAndCommandType(order.getId(), "EXPIRY")).thenReturn(Optional.of(expiry));
        assertThat(service.enqueueCancellation(order, null, "ORDER_SYSTEM", "retry", true)).isSameAs(expiry);
        var cancellation = service.enqueueCancellation(order, order.getUserId(), "CUSTOMER", "cancel", false);
        assertThat(cancellation.getId()).isNotEqualTo(expiry.getId());
        assertThat(expiry.getCommandType()).isEqualTo("EXPIRY");
        assertThat(cancellation.getCommandType()).isEqualTo("CANCELLATION");
    }
}
