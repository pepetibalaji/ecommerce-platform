package com.ecommerce.order.controller;

import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.dto.RequestOrderRefundRequest;
import com.ecommerce.order.dto.OrderOutboxReconciliationResponse;
import com.ecommerce.order.dto.OrderLifecycleAuditResponse;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.OrderOutboxReconciliationService;
import com.ecommerce.order.service.OrderLifecycleAuditService;
import com.ecommerce.common.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/admin/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Orders", description = "Admin order management APIs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderOutboxReconciliationService reconciliationService;
    private final OrderLifecycleAuditService auditService;

    public AdminOrderController(OrderService orderService) {
        this(orderService, null, null);
    }

    @Autowired
    public AdminOrderController(
            OrderService orderService,
            OrderOutboxReconciliationService reconciliationService,
            OrderLifecycleAuditService auditService
    ) {
        this.orderService = orderService;
        this.reconciliationService = reconciliationService;
        this.auditService = auditService;
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

    /** Source compatibility only; direct lifecycle mutation is not exposed over HTTP. */
    @Deprecated
    public OrderResponse updateOrderStatus(UUID id, UpdateOrderStatusRequest request) {
        return orderService.updateOrderStatus(id, request);
    }

    @PostMapping("/{id}/refund-requests")
    @Operation(
            summary = "Request a full refund for a confirmed order",
            description = "Writes an auditable durable command. The response is REFUND_REQUESTED until Payment Service reports an outcome."
    )
    public OrderResponse requestRefund(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RequestOrderRefundRequest request
    ) {
        return orderService.requestRefund(currentActorId(jwt), id, request.getReason());
    }

    @GetMapping("/reconciliation/outboxes")
    @Operation(summary = "Get durable order outbox reconciliation counts",
            description = "Read-only counts for order-created, inventory-release, checkout-compensation, and refund-request work.")
    public OrderOutboxReconciliationResponse getOutboxReconciliation() {
        if (reconciliationService == null) {
            throw new IllegalStateException("Outbox reconciliation service is unavailable");
        }
        return reconciliationService.snapshot();
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get immutable cancellation and refund audit history")
    public List<OrderLifecycleAuditResponse> getAudit(@PathVariable UUID id) {
        if (auditService == null) {
            throw new IllegalStateException("Order lifecycle audit service is unavailable");
        }
        return auditService.findForOrder(id).stream()
                .map(entry -> new OrderLifecycleAuditResponse(
                        entry.getId(), entry.getAction(), entry.getActorId(), entry.getActorType(),
                        entry.getReason(), entry.getRefundRequestId(), entry.getCreatedAt()))
                .toList();
    }

    private UUID currentActorId(Jwt jwt) {
        if (jwt == null || jwt.getClaimAsString("userId") == null || jwt.getClaimAsString("userId").isBlank()) {
            throw new BadRequestException("JWT userId claim is required");
        }
        try {
            return UUID.fromString(jwt.getClaimAsString("userId"));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("JWT userId claim must be a valid UUID");
        }
    }

}
