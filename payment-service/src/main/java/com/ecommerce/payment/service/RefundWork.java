package com.ecommerce.payment.service;

import com.ecommerce.payment.enums.PaymentProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot taken after a durable claim commits; contains no managed JPA entity. */
public record RefundWork(UUID refundId, UUID paymentId, UUID orderId, PaymentProvider provider,
                         BigDecimal amount, String currency, String reason,
                         String providerPaymentIntentId, String providerRefundId, String providerIdempotencyKey,
                         UUID leaseToken, Instant firstProviderAttemptAt) { }
