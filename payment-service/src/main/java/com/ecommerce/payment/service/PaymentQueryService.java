package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.response.AdminPaymentResponse;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Validated
public interface PaymentQueryService {

    Page<PaymentResponse> getMyPayments(
            @NotNull UUID userId,
            @NotNull Pageable pageable
    );

    PaymentResponse getPaymentByOrderIdForUser(
            @NotNull UUID orderId,
            @NotNull UUID userId
    );

    PaymentResponse getPaymentByIdForUser(
            @NotNull UUID paymentId,
            @NotNull UUID userId
    );

    Page<AdminPaymentResponse> getAdminPayments(
            PaymentStatus status,
            @NotNull Pageable pageable
    );

    AdminPaymentResponse getAdminPaymentById(@NotNull UUID paymentId);
}