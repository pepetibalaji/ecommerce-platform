package com.ecommerce.order.controller;

import java.util.UUID;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return orderService.createOrder(
                UUID.fromString(authentication.getName()),
                request
        );
    }

    @GetMapping
    public Page<OrderResponse> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return orderService.getMyOrders(
                UUID.fromString(authentication.getName()),
                PageRequest.of(page, size),
                status
        );
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        return orderService.getOrderById(
                UUID.fromString(authentication.getName()),
                id
        );
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        return orderService.cancelOrder(
                UUID.fromString(authentication.getName()),
                id
        );
    }

    @GetMapping("/admin")
    public Page<OrderResponse> getAdminOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return orderService.getAdminOrders(PageRequest.of(page, size), status);
    }

    @PutMapping("/admin/{id}/status")
    public OrderResponse updateOrderStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(id, request);
    }
}