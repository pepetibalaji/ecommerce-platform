package com.ecommerce.payment.controller;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.dto.response.CreateCheckoutSessionResponse;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.service.PaymentCheckoutService;
import com.ecommerce.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Customer payment APIs; provider redirects are informational and never prove payment.")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Payment state conflict; use the stable code and retry guidance", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.ecommerce.payment.dto.response.PaymentApiError.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Payment provider unavailable", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.ecommerce.payment.dto.response.PaymentApiError.class)))
})
public class PaymentController {

    private final PaymentCheckoutService paymentCheckoutService;

    private final PaymentQueryService paymentQueryService;

    private final com.ecommerce.payment.service.PaymentWebhookService paymentWebhookService;

    @PostMapping("/orders/{orderId}/refresh")
    public ResponseEntity<PaymentResponse> refreshPayment(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(paymentWebhookService.refreshPayment(orderId, extractUserId(jwt)));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Create or reuse an owned checkout session", description = "Requires a prepared payable Order. PAYMENT_PREPARING permits bounded retry after 2 seconds. Concurrent requests share one durable attempt. checkoutUrl is provider-allowlisted and expiresAt is UTC.")
    @PostMapping("/orders/{orderId}/checkout-session")
    public ResponseEntity<CreateCheckoutSessionResponse> createCheckoutSession(
            @PathVariable @NotNull(message = "Order id is required") UUID orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(paymentCheckoutService.createCheckoutSession(orderId, userId));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(paymentQueryService.getMyPayments(userId, com.ecommerce.payment.config.PaymentPagination.page(page, size)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @PathVariable @NotNull(message = "Order id is required") UUID orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(paymentQueryService.getPaymentByOrderIdForUser(orderId, userId));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentById(
            @PathVariable @NotNull(message = "Payment id is required") UUID paymentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(paymentQueryService.getPaymentByIdForUser(paymentId, userId));
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null) {
            throw new BadRequestException("JWT principal is required");
        }

        String userId = jwt.getClaimAsString("userId");

        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("JWT claim userId is required");
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("JWT claim userId must be a valid UUID");
        }
    }
}
