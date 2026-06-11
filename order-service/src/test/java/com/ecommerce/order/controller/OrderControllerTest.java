package com.ecommerce.order.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OrderController orderController;

    @Test
    void shouldCreateOrder() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(authentication.getName()).thenReturn(userId.toString());

        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(UUID.randomUUID());
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        OrderResponse response = new OrderResponse(
                UUID.randomUUID(),
                userId,
                new BigDecimal("200.00"),
                OrderStatus.PENDING,
                LocalDateTime.now(),
                List.of(new OrderItemResponse(
                        UUID.randomUUID(),
                        item.getProductId(),
                        2,
                        item.getPrice()
                ))
        );

        when(orderService.createOrder(any(), any())).thenReturn(response);

        OrderResponse result = orderController.createOrder(authentication, request);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void shouldGetMyOrders() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(authentication.getName()).thenReturn(userId.toString());

        when(orderService.getMyOrders(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = orderController.getMyOrders(authentication, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void shouldCancelOrder() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(authentication.getName()).thenReturn(userId.toString());

        UUID orderId = UUID.randomUUID();

        OrderResponse response = new OrderResponse(
                orderId,
                userId,
                new BigDecimal("200.00"),
                OrderStatus.CANCELLED,
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.cancelOrder(any(), any())).thenReturn(response);

        OrderResponse result = orderController.cancelOrder(authentication, orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getId()).isEqualTo(orderId);
    }

    @Test
    void shouldUpdateOrderStatus() {
        UUID orderId = UUID.randomUUID();

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CONFIRMED);

        OrderResponse response = new OrderResponse(
                orderId,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("200.00"),
                OrderStatus.CONFIRMED,
                LocalDateTime.now(),
                List.of()
        );

        when(orderService.updateOrderStatus(any(), any())).thenReturn(response);

        OrderResponse result = orderController.updateOrderStatus(orderId, request);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getId()).isEqualTo(orderId);
    }
}