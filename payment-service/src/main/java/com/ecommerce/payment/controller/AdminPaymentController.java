package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.response.AdminPaymentResponse;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Payments", description = "Admin payment lookup APIs")
public class AdminPaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping
    public ResponseEntity<Page<AdminPaymentResponse>> getPayments(
            @RequestParam(required = false) PaymentStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(paymentQueryService.getAdminPayments(status, pageable));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<AdminPaymentResponse> getPaymentById(
            @PathVariable @NotNull(message = "Payment id is required") UUID paymentId
    ) {
        return ResponseEntity.ok(paymentQueryService.getAdminPaymentById(paymentId));
    }
}