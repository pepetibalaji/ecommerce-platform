package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.request.CreatePaymentRefundRequest;
import com.ecommerce.payment.dto.response.PaymentRefundResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Validated
public interface PaymentRefundService {

    PaymentRefundResponse createPaymentRefund(@Valid CreatePaymentRefundRequest request);

    PaymentRefundResponse getPaymentRefundById(@NotNull UUID refundId);

    List<PaymentRefundResponse> getPaymentRefundsByPaymentId(@NotNull UUID paymentId);

    PaymentRefundResponse getPaymentRefundByIdempotencyKey(@NotBlank String idempotencyKey);

    boolean existsByIdempotencyKey(@NotBlank String idempotencyKey);
}