package com.ecommerce.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryGrpcClient;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            InventoryGrpcClient inventoryGrpcClient
    ) {
        this.orderRepository = orderRepository;
        this.inventoryGrpcClient = inventoryGrpcClient;
    }

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        validateAndReserveStock(request);

        try {
            Order order = new Order();
            order.setUserId(userId);

            BigDecimal totalAmount = BigDecimal.ZERO;

            for (CreateOrderItemRequest itemRequest : request.getItems()) {
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProductId(itemRequest.getProductId());
                item.setQuantity(itemRequest.getQuantity());
                item.setPrice(itemRequest.getPrice());

                order.getItems().add(item);

                totalAmount = totalAmount.add(
                        itemRequest.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                );
            }

            order.setTotalAmount(totalAmount);
            order.setStatus(OrderStatus.PENDING);

            Order saved = orderRepository.save(order);
            return toResponse(saved);
        } catch (RuntimeException ex) {
            releaseReservedStock(request);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(
            UUID userId,
            Pageable pageable,
            OrderStatus status
    ) {
        Page<Order> page = (status == null)
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);

        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        return toResponse(order);
    }

    @Override
    public OrderResponse cancelOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        validateStatusTransition(order.getStatus(), OrderStatus.CANCELLED);

        releaseReservedStock(order);

        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAdminOrders(Pageable pageable, OrderStatus status) {
        Page<Order> page = (status == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);

        return page.map(this::toResponse);
    }

    @Override
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        validateStatusTransition(order.getStatus(), request.getStatus());

        order.setStatus(request.getStatus());
        return toResponse(orderRepository.save(order));
    }

    private void validateAndReserveStock(CreateOrderRequest request) {
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            var inventory = inventoryGrpcClient.getInventory(itemRequest.getProductId());

            if (inventory.getAvailableStock() < itemRequest.getQuantity()) {
                throw new IllegalStateException(
                        "Insufficient stock for product: " + itemRequest.getProductId()
                );
            }
        }

        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            inventoryGrpcClient.reserveStock(
                    itemRequest.getProductId(),
                    itemRequest.getQuantity()
            );
        }
    }

    private void releaseReservedStock(CreateOrderRequest request) {
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            try {
                inventoryGrpcClient.releaseStock(
                        itemRequest.getProductId(),
                        itemRequest.getQuantity()
                );
            } catch (Exception ignored) {
                // best-effort rollback
            }
        }
    }

    private void releaseReservedStock(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                inventoryGrpcClient.releaseStock(
                        item.getProductId(),
                        item.getQuantity()
                );
            } catch (Exception ignored) {
                // best-effort rollback
            }
        }
    }

    private void validateStatusTransition(
            OrderStatus currentStatus,
            OrderStatus targetStatus
    ) {
        if (currentStatus == targetStatus) {
            return;
        }

        switch (currentStatus) {
            case PENDING -> {
                if (targetStatus != OrderStatus.CONFIRMED &&
                        targetStatus != OrderStatus.CANCELLED) {
                    throw new IllegalStateException(
                            "Invalid transition from PENDING to " + targetStatus
                    );
                }
            }
            case CONFIRMED -> {
                if (targetStatus != OrderStatus.CANCELLED) {
                    throw new IllegalStateException(
                            "Invalid transition from CONFIRMED to " + targetStatus
                    );
                }
            }
            case CANCELLED -> throw new IllegalStateException(
                    "Cancelled orders cannot change state"
            );
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                items
        );
    }
}