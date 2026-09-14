package com.ecommerce.order.controller;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.api.OrderApiException;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        @Operation(
                summary = "Create an idempotent order",
                description = "Requires a customer-scoped Idempotency-Key. Reuse it only with the same checkout payload."
        )
        @ApiResponses({
                @ApiResponse(responseCode = "201", description = "Order created, or the prior matching order replayed"),
                @ApiResponse(responseCode = "400", description = "Invalid checkout request or missing/invalid idempotency key"),
                @ApiResponse(responseCode = "409", description = "Idempotency key reused with a different request or checkout state conflict"),
                @ApiResponse(responseCode = "503", description = "Catalogue or Inventory temporarily unavailable; retry with the same key")
        })
        public OrderResponse createOrder(
                @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                @Valid @RequestBody CreateOrderRequest request,
                @Parameter(
                        name = "Idempotency-Key",
                        in = ParameterIn.HEADER,
                        required = true,
                        description = "Opaque checkout-attempt key, maximum 100 trimmed characters."
                )
                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
        ) {
                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                        throw new OrderApiException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                                "Idempotency-Key is required.", false, java.util.List.of());
                }
                return orderService.createOrder(
                        currentUserId(jwt),
                        request,
                        idempotencyKey
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
                validatePage(page, size);
                return orderService.getMyOrders(
                        currentUserId(jwt),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                        status
                );
        }

        /** Java-call compatibility; HTTP clients must supply Idempotency-Key. */
        @Deprecated
        public OrderResponse createOrder(Jwt jwt, CreateOrderRequest request) {
                return orderService.createOrder(currentUserId(jwt), request, null);
        }

        private void validatePage(int page, int size) {
                if (page < 0) throw new OrderApiException("PAGE_OUT_OF_RANGE", HttpStatus.BAD_REQUEST, "page must be zero or greater.", false, java.util.List.of());
                if (size < 1 || size > 50) throw new OrderApiException("PAGE_SIZE_OUT_OF_RANGE", HttpStatus.BAD_REQUEST, "size must be between 1 and 50.", false, java.util.List.of());
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
        @Operation(
                summary = "Cancel current user's order",
                description = "Cancels a pending order or requests a full refund for a confirmed order; it never performs a generic status transition."
        )
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
