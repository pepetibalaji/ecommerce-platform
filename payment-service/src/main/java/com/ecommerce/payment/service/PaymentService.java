package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.request.CreatePaymentRequest;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Validated
public interface PaymentService {

    PaymentResponse createPayment(@Valid CreatePaymentRequest request);

    PaymentResponse getPaymentById(@NotNull UUID paymentId);

    PaymentResponse getPaymentByOrderId(@NotNull UUID orderId);

    Page<PaymentResponse> getPaymentsByUserId(
            @NotNull UUID userId,
            @NotNull Pageable pageable
    );

    Page<PaymentResponse> getPaymentsByStatus(
            @NotNull PaymentStatus status,
            @NotNull Pageable pageable
    );

    boolean existsByOrderId(@NotNull UUID orderId);

    boolean existsByIdempotencyKey(@NotNull String idempotencyKey);
}