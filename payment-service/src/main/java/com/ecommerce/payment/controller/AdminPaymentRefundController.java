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
            @Valid @RequestBody AdminRefundRequest request
    ) {
        var result = paymentRefundService.refundPayment(
                paymentId,
                request.orderId(),
                request.amount(),
                request.currency(),
                request.reason(),
                request.idempotencyKey()
        );

        return ResponseEntity.accepted().body(result);
    }
}