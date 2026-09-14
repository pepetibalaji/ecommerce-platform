package com.ecommerce.payment.service.impl;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.payment.dto.request.CreatePaymentRequest;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.order.TrustedOrderClient;
import com.ecommerce.payment.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceImplTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentMapper mapper = mock(PaymentMapper.class);
    PaymentMetrics metrics = mock(PaymentMetrics.class);
    TrustedOrderClient orders = mock(TrustedOrderClient.class);
    PaymentCancellationRequestRepository cancellations = mock(PaymentCancellationRequestRepository.class);
    PaymentServiceImpl service = new PaymentServiceImpl(payments, mapper, metrics, orders, cancellations);
    UUID orderId = UUID.randomUUID(), userId = UUID.randomUUID();

    @Test void arbitraryPaymentCreationIsRefusedWithoutDatabaseOrProviderWork() {
        var request = new CreatePaymentRequest();
        request.setOrderId(orderId); request.setUserId(userId);
        request.setAmount(new BigDecimal("12.50")); request.setCurrency("USD");
        request.setProvider(PaymentProvider.SANDBOX); request.setIdempotencyKey("arbitrary-payment");
        assertThatThrownBy(() -> service.createPayment(request)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(payments, mapper, metrics, orders, cancellations);
    }

    @Test void preparationValidatesTrustedNormalizedOrderBeforePersistingImmutableTotals() {
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0); payment.setId(UUID.randomUUID()); return payment;
        });
        service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.5"), " usd ", "correlation", "trace");
        var saved = ArgumentCaptor.forClass(Payment.class);
        var ordered = inOrder(orders, payments, metrics);
        ordered.verify(orders).validatePreparation(orderId, userId, new BigDecimal("12.50"), "USD", false);
        ordered.verify(payments).saveAndFlush(saved.capture());
        ordered.verify(metrics).paymentCreated();
        assertThat(saved.getValue()).satisfies(payment -> {
            assertThat(payment.getOrderId()).isEqualTo(orderId);
            assertThat(payment.getUserId()).isEqualTo(userId);
            assertThat(payment.getAmount()).isEqualByComparingTo("12.50");
            assertThat(payment.getCurrency()).isEqualTo("USD");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getIdempotencyKey()).isEqualTo("order-created:" + orderId);
            assertThat(payment.getCorrelationId()).isEqualTo("correlation");
            assertThat(payment.getTraceId()).isEqualTo("trace");
        });
    }

    @Test void matchingDuplicateIsHarmlessAfterPaymentHasCompleted() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(prepared()));
        service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.500"), "usd", "another-correlation", "another-trace");
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(orders, metrics, cancellations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "amount", "currency"})
    void conflictingDuplicateCannotChangePreparedPayment(String conflict) {
        var prepared = prepared();
        switch (conflict) {
            case "owner" -> prepared.setUserId(UUID.randomUUID());
            case "amount" -> prepared.setAmount(new BigDecimal("99.00"));
            case "currency" -> prepared.setCurrency("EUR");
            default -> throw new AssertionError(conflict);
        }
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(prepared));
        assertThatThrownBy(() -> service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.50"), "USD", null, null))
                .isInstanceOf(BadRequestException.class);
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(orders, metrics, cancellations);
    }

    @Test void cancellationTombstoneIsTheOnlyPreparationOverrideSentToTrustedOrder() {
        when(cancellations.existsByOrderId(orderId)).thenReturn(true);
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.50"), "USD", null, null);
        verify(orders).validatePreparation(orderId, userId, new BigDecimal("12.50"), "USD", true);
        verify(payments).saveAndFlush(any());
    }

    @Test void staleOrUnavailableTrustedOrderNeverPersistsPayment() {
        doThrow(new BadRequestException("stale order")).when(orders)
                .validatePreparation(orderId, userId, new BigDecimal("12.50"), "USD", false);
        assertThatThrownBy(() -> service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.50"), "USD", null, null))
                .isInstanceOf(BadRequestException.class);
        doThrow(new IllegalStateException("lookup unavailable")).when(orders)
                .validatePreparation(orderId, userId, new BigDecimal("12.50"), "USD", false);
        assertThatThrownBy(() -> service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.50"), "USD", null, null))
                .isInstanceOf(IllegalStateException.class);
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(metrics);
    }

    @ParameterizedTest
    @MethodSource("malformedInputs")
    void malformedInputsCannotReachTrustedLookupOrPersistence(UUID order, UUID user, BigDecimal amount, String currency) {
        assertThatThrownBy(() -> service.preparePaymentFromOrder(order, user, amount, currency, null, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(payments, orders, cancellations, metrics);
    }

    static Stream<Arguments> malformedInputs() {
        UUID order = UUID.randomUUID(), user = UUID.randomUUID();
        return Stream.of(Arguments.of(null, user, BigDecimal.ONE, "USD"),
                Arguments.of(order, null, BigDecimal.ONE, "USD"),
                Arguments.of(order, user, null, "USD"),
                Arguments.of(order, user, BigDecimal.ZERO, "USD"),
                Arguments.of(order, user, new BigDecimal("-1"), "USD"),
                Arguments.of(order, user, BigDecimal.ONE, null),
                Arguments.of(order, user, BigDecimal.ONE, " "),
                Arguments.of(order, user, BigDecimal.ONE, "USDX"),
                Arguments.of(order, user, new BigDecimal("1.001"), "USD"),
                Arguments.of(order, user, new BigDecimal("100000000000000000.00"), "USD"));
    }

    @Test void uniquenessRacePropagatesForFreshTransactionRetryWithoutQueryingAbortedTransaction() {
        var failure = new org.springframework.dao.DataIntegrityViolationException("duplicate order");
        when(payments.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.50"), "USD", null, null))
                .isSameAs(failure);
        verify(payments, times(1)).findByOrderId(orderId);
        verify(payments, never()).existsByOrderId(any());
        verify(payments, never()).existsByIdempotencyKey(any());
        verifyNoInteractions(metrics);
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(prepared()));
        service.preparePaymentFromOrder(orderId, userId, new BigDecimal("12.50"), "USD", null, null);
        verify(payments, times(1)).saveAndFlush(any());
    }

    private Payment prepared() {
        return Payment.builder().id(UUID.randomUUID()).orderId(orderId).userId(userId)
                .amount(new BigDecimal("12.50")).currency("USD").provider(PaymentProvider.STRIPE)
                .status(PaymentStatus.SUCCESS).idempotencyKey("order-created:" + orderId).build();
    }
}
