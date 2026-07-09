package com.ecommerce.order.controller;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Customer order APIs")
public class OrderController {

        private static final String USER_ID_CLAIM = "userId";
        private final OrderService orderService;

        public OrderController(OrderService orderService) {
                this.orderService = orderService;
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create order")
        public OrderResponse createOrder(
                @Parameter(hidden = true)
                @AuthenticationPrincipal Jwt jwt,

                @Valid
                @RequestBody CreateOrderRequest request
        ) {
                return orderService.createOrder(
                        currentUserId(jwt),
                        request
                );
        }

        @GetMapping
        @Operation(summary = "Get current user's orders")
        public Page<OrderResponse> getMyOrders(
                @Parameter(hidden = true)
                @AuthenticationPrincipal Jwt jwt,

                @RequestParam(required = false)
                OrderStatus status,

                @RequestParam(defaultValue = "0")
                int page,

                @RequestParam(defaultValue = "10")
                int size
        ) {
                return orderService.getMyOrders(
                        currentUserId(jwt),
                        PageRequest.of(page, size),
                        status
                );
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get current user's order by id")
        public OrderResponse getOrderById(
                @Parameter(hidden = true)
                @AuthenticationPrincipal Jwt jwt,

                @PathVariable UUID id
        ) {
                return orderService.getOrderById(
                        currentUserId(jwt),
                        id
                );
        }

        @PutMapping("/{id}/cancel")
        @Operation(summary = "Cancel current user's order")
        public OrderResponse cancelOrder(
                @Parameter(hidden = true)
                @AuthenticationPrincipal Jwt jwt,

                @PathVariable UUID id
        ) {
                return orderService.cancelOrder(
                        currentUserId(jwt),
                        id
                );
        }

        private UUID currentUserId(Jwt jwt) {
                if (jwt == null) {
                throw new BadRequestException("Authenticated user is required");
                }

                String userId = jwt.getClaimAsString(USER_ID_CLAIM);

                if (userId == null || userId.isBlank()) {
                throw new BadRequestException("JWT userId claim is required");
                }

                try {
                return UUID.fromString(userId);
                } catch (IllegalArgumentException ex) {
                throw new BadRequestException("JWT userId claim must be a valid UUID");
                }
        }
}