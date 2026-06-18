package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminOrderController adminOrderController;

    @Test
    void shouldGetAdminOrders() {

        Page<OrderResponse> page =
                new PageImpl<>(List.of());

        when(orderService.getAdminOrders(
                eq(PageRequest.of(0, 10)),
                isNull()
        )).thenReturn(page);

        Page<OrderResponse> result =
                adminOrderController.getAdminOrders(
                        null,
                        0,
                        10
                );

        assertThat(result.getContent())
                .isEmpty();

        verify(orderService)
                .getAdminOrders(
                        eq(PageRequest.of(0, 10)),
                        isNull()
                );
    }

    @Test
    void shouldGetAdminOrdersByStatus() {

        Page<OrderResponse> page =
                new PageImpl<>(List.of());

        when(orderService.getAdminOrders(
                eq(PageRequest.of(1, 5)),
                eq(OrderStatus.PENDING)
        )).thenReturn(page);

        Page<OrderResponse> result =
                adminOrderController.getAdminOrders(
                        OrderStatus.PENDING,
                        1,
                        5
                );

        assertThat(result.getContent())
                .isEmpty();

        verify(orderService)
                .getAdminOrders(
                        eq(PageRequest.of(1, 5)),
                        eq(OrderStatus.PENDING)
                );
    }

    @Test
    void shouldUpdateOrderStatus() {

        UUID orderId =
                UUID.randomUUID();

        UpdateOrderStatusRequest request =
                new UpdateOrderStatusRequest();

        request.setStatus(OrderStatus.CONFIRMED);

        OrderResponse response =
                new OrderResponse(
                        orderId,
                        USER_ID,
                        new BigDecimal("200.00"),
                        OrderStatus.CONFIRMED,
                        LocalDateTime.now(),
                        List.of()
                );

        when(orderService.updateOrderStatus(
                orderId,
                request
        )).thenReturn(response);

        OrderResponse result =
                adminOrderController.updateOrderStatus(
                        orderId,
                        request
                );

        assertThat(result.getId())
                .isEqualTo(orderId);

        assertThat(result.getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        verify(orderService)
                .updateOrderStatus(
                        orderId,
                        request
                );
    }
}