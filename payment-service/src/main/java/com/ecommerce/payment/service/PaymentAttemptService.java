package com.ecommerce.payment.service;

import com.ecommerce.payment.dto.request.CreatePaymentAttemptRequest;
import com.ecommerce.payment.dto.response.PaymentAttemptResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Validated
public interface PaymentAttemptService {

    PaymentAttemptResponse createPaymentAttempt(@Valid CreatePaymentAttemptRequest request);

    PaymentAttemptResponse getPaymentAttemptById(@NotNull UUID paymentAttemptId);

    List<PaymentAttemptResponse> getPaymentAttemptsByPaymentId(@NotNull UUID paymentId);

    PaymentAttemptResponse getLatestPaymentAttemptByPaymentId(@NotNull UUID paymentId);

    PaymentAttemptResponse getPaymentAttemptByProviderSessionId(@NotBlank String providerSessionId);

    PaymentAttemptResponse getPaymentAttemptByProviderPaymentIntentId(@NotBlank String providerPaymentIntentId);
}