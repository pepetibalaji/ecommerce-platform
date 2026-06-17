package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Orders", description = "Admin order management APIs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Get all orders")
    public Page<OrderResponse> getAdminOrders(
            @RequestParam(required = false)
            OrderStatus status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {
        return orderService.getAdminOrders(
                PageRequest.of(page, size),
                status
        );
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update order status")
    public OrderResponse updateOrderStatus(
            @PathVariable UUID id,

            @Valid
            @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(
                id,
                request
        );
    }
}