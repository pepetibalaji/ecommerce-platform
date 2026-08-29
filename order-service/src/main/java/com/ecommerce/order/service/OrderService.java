package com.ecommerce.order.service;

import java.util.UUID;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.dto.SellerOrderResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.common.events.payment.PaymentFailedEvent;
import com.ecommerce.common.events.payment.PaymentSuccessEvent;
import com.ecommerce.common.events.payment.PaymentRefundCompletedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable, OrderStatus status);

    OrderResponse getOrderById(UUID userId, UUID orderId);

    OrderResponse cancelOrder(UUID userId, UUID orderId);

    Page<OrderResponse> getAdminOrders(Pageable pageable, OrderStatus status);

    OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request);

    Page<SellerOrderResponse> getSellerOrders(UUID sellerId, Pageable pageable);

    void handlePaymentSuccess(PaymentSuccessEvent event);

    void handlePaymentFailure(PaymentFailedEvent event);

    void handleRefundCompleted(PaymentRefundCompletedEvent event);
}
