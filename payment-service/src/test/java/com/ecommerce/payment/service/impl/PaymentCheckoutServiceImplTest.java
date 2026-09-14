package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.config.*;
import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.exception.*;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.order.TrustedOrderClient;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentCheckoutServiceImplTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    PaymentGatewayFactory gateways = mock(PaymentGatewayFactory.class);
    PaymentGateway gateway = mock(PaymentGateway.class);
    PaymentCancellationRequestRepository cancellations = mock(PaymentCancellationRequestRepository.class);
    TrustedOrderClient trustedOrders = mock(TrustedOrderClient.class);
    PaymentProviderProperties settings = new PaymentProviderProperties();
    Payment payment;
    PaymentAttempt attempt;
    CheckoutSessionTransactions transactions;
    PaymentCheckoutServiceImpl service;

    @BeforeEach void setUp() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("test");
        transactions = new CheckoutSessionTransactions(payments, attempts, gateways, settings,
                new CheckoutUrlPolicy(settings, environment), new PaymentMetrics(new SimpleMeterRegistry()), cancellations, trustedOrders);
        service = new PaymentCheckoutServiceImpl(transactions);
        payment = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("10.00")).currency("USD").provider(PaymentProvider.SANDBOX)
                .status(PaymentStatus.PENDING).idempotencyKey("payment-key").build();
        attempt = PaymentAttempt.builder().id(UUID.randomUUID()).payment(payment).provider(PaymentProvider.SANDBOX)
                .idempotencyKey("checkout:durable").successUrl("http://localhost:5173/payment/return")
                .cancelUrl("http://localhost:5173/payment/return").expiresAt(Instant.now().plusSeconds(1800))
                .status(PaymentAttemptStatus.CREATED).build();
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.of(payment));
        when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
    }

    @Test void returnsExistingActiveSessionWithoutCreatingProviderSession() {
        attempt.setProviderSessionId("saved-session");
        attempt.setCheckoutUrl("http://localhost:3001/mock-checkout");
        attempt.setStatus(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION);
        payment.setStatus(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        when(attempts.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(eq(payment.getId()), anyList()))
                .thenReturn(Optional.of(attempt));
        var result = service.createCheckoutSession(payment.getOrderId(), payment.getUserId());
        assertThat(result.getCheckoutUrl()).isEqualTo(attempt.getCheckoutUrl());
        verifyNoInteractions(gateway, gateways);
    }

    @Test void providerTimeoutReplaysPersistedKeyAndRequestParameters() {
        when(attempts.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(eq(payment.getId()), anyList()))
                .thenReturn(Optional.of(attempt));
        when(gateways.getGateway(PaymentProvider.SANDBOX)).thenReturn(gateway);
        when(gateway.createCheckoutSession(any())).thenThrow(new IllegalStateException("secret provider detail"))
                .thenReturn(CheckoutSessionResult.builder().provider(PaymentProvider.SANDBOX)
                        .providerSessionId("same-session").checkoutUrl("http://localhost:3001/mock-checkout")
                        .expiresAt(attempt.getExpiresAt()).build());
        assertThatThrownBy(() -> service.createCheckoutSession(payment.getOrderId(), payment.getUserId()))
                .isInstanceOf(PaymentApiException.class).hasMessageNotContaining("secret");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CREATED);
        service.createCheckoutSession(payment.getOrderId(), payment.getUserId());
        var commands = ArgumentCaptor.forClass(CreateCheckoutSessionCommand.class);
        verify(gateway, times(2)).createCheckoutSession(commands.capture());
        assertThat(commands.getAllValues()).extracting(CreateCheckoutSessionCommand::getIdempotencyKey)
                .containsExactly("checkout:durable", "checkout:durable");
        assertThat(commands.getAllValues()).extracting(CreateCheckoutSessionCommand::getExpiresAt)
                .containsOnly(attempt.getExpiresAt());
        verify(attempts, never()).save(any());
    }

    @Test void reservesUniqueAttemptKeyBeforeAnyProviderExecution() {
        when(attempts.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentAttempt saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        transactions.reserve(payment.getOrderId(), payment.getUserId());
        var reserved = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attempts).saveAndFlush(reserved.capture());
        assertThat(reserved.getValue().getIdempotencyKey()).startsWith("checkout:").hasSize(45);
        assertThat(reserved.getValue().getSuccessUrl()).contains(payment.getId().toString(), payment.getOrderId().toString());
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "FAILED", "CANCELLED", "EXPIRED",
            "PROCESSING", "REFUNDED", "REFUND_REQUESTED", "REFUND_PROCESSING", "REFUND_FAILED"})
    void rejectsTerminalOrProcessingPayment(PaymentStatus status) {
        payment.setStatus(status);
        assertThatThrownBy(() -> service.createCheckoutSession(payment.getOrderId(), payment.getUserId()))
                .isInstanceOf(PaymentApiException.class);
        verifyNoInteractions(gateways, gateway);
    }

    @Test void rejectsExpiredAttemptWithoutMakingReplacementSession() {
        attempt.setExpiresAt(Instant.now().minusSeconds(1));
        when(attempts.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(eq(payment.getId()), anyList()))
                .thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.createCheckoutSession(payment.getOrderId(), payment.getUserId()))
                .isInstanceOfSatisfying(PaymentApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(PaymentErrorCode.PAYMENT_CHECKOUT_SESSION_EXPIRED));
        verifyNoInteractions(gateways, gateway);
    }

    @Test void refusesCancellationTombstoneAndWrongOwner() {
        when(cancellations.existsByOrderId(payment.getOrderId())).thenReturn(true);
        assertThatThrownBy(() -> service.createCheckoutSession(payment.getOrderId(), payment.getUserId()))
                .isInstanceOfSatisfying(PaymentApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(PaymentErrorCode.PAYMENT_CANCELLED));
        assertThatThrownBy(() -> service.createCheckoutSession(payment.getOrderId(), UUID.randomUUID()))
                .isInstanceOfSatisfying(PaymentApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_OWNED));
        verifyNoInteractions(gateways, gateway);
    }
}
