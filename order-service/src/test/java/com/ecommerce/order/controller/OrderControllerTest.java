package com.ecommerce.order.controller;

import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
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

import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID PRODUCT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void shouldCreateOrder() {

        CreateOrderItemRequest item =
                new CreateOrderItemRequest();

        item.setProductId(PRODUCT_ID);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));

        CreateOrderRequest request =
                new CreateOrderRequest();

        request.setItems(List.of(item));

        UUID orderId =
                UUID.randomUUID();

        OrderResponse response =
                new OrderResponse(
                        orderId,
                        USER_ID,
                        new BigDecimal("200.00"),
                        OrderStatus.PENDING,
                        LocalDateTime.now(),
                        List.of(
                                new OrderItemResponse(
                                        UUID.randomUUID(),
                                        PRODUCT_ID,
                                        2,
                                        new BigDecimal("100.00")
                                )
                        )
                );

        when(orderService.createOrder(
                eq(USER_ID),
                any(CreateOrderRequest.class)
        )).thenReturn(response);

        OrderResponse result =
                orderController.createOrder(
                        jwt(),
                        request
                );

        assertThat(result.getId())
                .isEqualTo(orderId);

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getStatus())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(result.getItems())
                .hasSize(1);

        assertThat(result.getItems().get(0).getProductId())
                .isEqualTo(PRODUCT_ID);

        assertThat(result.getItems().get(0).getQuantity())
                .isEqualTo(2);

        verify(orderService)
                .createOrder(
                        eq(USER_ID),
                        any(CreateOrderRequest.class)
                );
    }

    @Test
    void shouldGetMyOrders() {

        Page<OrderResponse> page =
                new PageImpl<>(List.of());

        when(orderService.getMyOrders(
                eq(USER_ID),
                eq(PageRequest.of(0, 10)),
                isNull()
        )).thenReturn(page);

        Page<OrderResponse> result =
                orderController.getMyOrders(
                        jwt(),
                        null,
                        0,
                        10
                );

        assertThat(result.getContent())
                .isEmpty();

        verify(orderService)
                .getMyOrders(
                        eq(USER_ID),
                        eq(PageRequest.of(0, 10)),
                        isNull()
                );
    }

    @Test
    void shouldGetMyOrdersByStatus() {

        Page<OrderResponse> page =
                new PageImpl<>(List.of());

        when(orderService.getMyOrders(
                eq(USER_ID),
                eq(PageRequest.of(1, 5)),
                eq(OrderStatus.PENDING)
        )).thenReturn(page);

        Page<OrderResponse> result =
                orderController.getMyOrders(
                        jwt(),
                        OrderStatus.PENDING,
                        1,
                        5
                );

        assertThat(result.getContent())
                .isEmpty();

        verify(orderService)
                .getMyOrders(
                        eq(USER_ID),
                        eq(PageRequest.of(1, 5)),
                        eq(OrderStatus.PENDING)
                );
    }

    @Test
    void shouldGetOrderById() {

        UUID orderId =
                UUID.randomUUID();

        OrderResponse response =
                new OrderResponse(
                        orderId,
                        USER_ID,
                        new BigDecimal("200.00"),
                        OrderStatus.PENDING,
                        LocalDateTime.now(),
                        List.of()
                );

        when(orderService.getOrderById(
                USER_ID,
                orderId
        )).thenReturn(response);

        OrderResponse result =
                orderController.getOrderById(
                        jwt(),
                        orderId
                );

        assertThat(result.getId())
                .isEqualTo(orderId);

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getStatus())
                .isEqualTo(OrderStatus.PENDING);

        verify(orderService)
                .getOrderById(
                        USER_ID,
                        orderId
                );
    }

    @Test
    void shouldCancelOrder() {

        UUID orderId =
                UUID.randomUUID();

        OrderResponse response =
                new OrderResponse(
                        orderId,
                        USER_ID,
                        new BigDecimal("200.00"),
                        OrderStatus.CANCELLED,
                        LocalDateTime.now(),
                        List.of()
                );

        when(orderService.cancelOrder(
                USER_ID,
                orderId
        )).thenReturn(response);

        OrderResponse result =
                orderController.cancelOrder(
                        jwt(),
                        orderId
                );

        assertThat(result.getId())
                .isEqualTo(orderId);

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        verify(orderService)
                .cancelOrder(
                        USER_ID,
                        orderId
                );
    }

    private Jwt jwt() {

        Instant now =
                Instant.now();

        return Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("customer@example.com")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("userId", USER_ID.toString())
                .claim("role", "CUSTOMER")
                .claim("status", "ACTIVE")
                .claim("tokenVersion", 0L)
                .build();
    }
}