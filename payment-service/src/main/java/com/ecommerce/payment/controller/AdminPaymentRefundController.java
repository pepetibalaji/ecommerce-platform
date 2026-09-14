package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.request.AdminRefundRequest;
import com.ecommerce.payment.dto.response.AdminRefundResponse;
import com.ecommerce.payment.service.PaymentRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentRefundController {

    private final PaymentRefundService paymentRefundService;

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRefundResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody AdminRefundRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt
    ) {
        var result = paymentRefundService.refundPayment(
                paymentId,
                request.orderId(),
                request.amount(),
                request.currency(),
                request.reason(),
                request.idempotencyKey(),
                new com.ecommerce.payment.service.RefundAudit(null,
                        com.ecommerce.common.security.util.JwtPrincipalUtils.getUserId(jwt), "ADMIN",
                        org.slf4j.MDC.get("correlationId"), org.slf4j.MDC.get("traceId"), java.time.Instant.now())
        );

        return ResponseEntity.accepted().body(result);
    }
    public record ReconcileRefundRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 5000) String reason) { }

    @PostMapping("/{paymentId}/refunds/{refundId}/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRefundResponse> reconcileRefund(
            @PathVariable UUID paymentId, @PathVariable UUID refundId,
            @Valid @RequestBody ReconcileRefundRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return ResponseEntity.accepted().body(paymentRefundService.reconcileRefund(paymentId, refundId,
                com.ecommerce.common.security.util.JwtPrincipalUtils.getUserId(jwt), request.reason()));
    }
}
