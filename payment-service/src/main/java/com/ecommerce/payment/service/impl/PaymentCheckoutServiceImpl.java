package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.dto.response.CreateCheckoutSessionResponse;
import com.ecommerce.payment.service.PaymentCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCheckoutServiceImpl implements PaymentCheckoutService {
    private final CheckoutSessionTransactions transactions;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreateCheckoutSessionResponse createCheckoutSession(UUID orderId, UUID userId) {
        // Reservation MUST commit before touching the provider. On timeout/crash the same durable
        // attempt and immutable parameters are replayed using the same provider idempotency key.
        UUID attemptId = transactions.reserve(orderId, userId);
        return transactions.complete(orderId, userId, attemptId);
    }
}
