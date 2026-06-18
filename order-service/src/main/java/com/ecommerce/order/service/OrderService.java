package com.ecommerce.order.service;

import java.util.UUID;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable, OrderStatus status);

    OrderResponse getOrderById(UUID userId, UUID orderId);

    OrderResponse cancelOrder(UUID userId, UUID orderId);

    Page<OrderResponse> getAdminOrders(Pageable pageable, OrderStatus status);

    OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request);
}