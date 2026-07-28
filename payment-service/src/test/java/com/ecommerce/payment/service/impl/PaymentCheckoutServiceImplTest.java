package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.dto.response.CreateCheckoutSessionResponse;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.PaymentGateway;
import com.ecommerce.payment.provider.PaymentGatewayFactory;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCheckoutServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentGatewayFactory paymentGatewayFactory;
    @Mock private PaymentMetrics paymentMetrics;
    @InjectMocks private PaymentCheckoutServiceImpl paymentCheckoutService;

    @Test
    void returnsExistingActiveAttemptWithoutCreatingAnotherProviderSession() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = payment(paymentId, orderId, userId);
        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(payment)
                .provider(PaymentProvider.SANDBOX)
                .providerSessionId("sandbox-session")
                .checkoutUrl("https://checkout.example/session")
                .status(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentAttemptRepository
                .findTopByPayment_IdAndStatusInAndExpiresAtAfterOrderByCreatedAtDesc(
                        eq(paymentId), anyList(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(attempt));

        CreateCheckoutSessionResponse result = paymentCheckoutService.createCheckoutSession(orderId, userId);

        assertThat(result.getPaymentId()).isEqualTo(paymentId);
        assertThat(result.getCheckoutUrl()).isEqualTo("https://checkout.example/session");
        verifyNoInteractions(paymentGatewayFactory, paymentMetrics);
        verify(paymentAttemptRepository, never()).save(any());
    }

    private Payment payment(UUID paymentId, UUID orderId, UUID userId) {
        return Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId(userId)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .provider(PaymentProvider.SANDBOX)
                .status(PaymentStatus.PENDING)
                .idempotencyKey("payment-key")
                .build();
    }
}
