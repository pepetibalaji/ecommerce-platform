package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundWorkflowTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    PaymentRefundWorkflow workflow = new PaymentRefundWorkflow(refunds, payments, publisher,
            mock(PaymentMetrics.class), new SimpleMeterRegistry());
    Payment payment;
    PaymentRefund refund;
    RefundWork work;

    @BeforeEach void setup() {
        payment = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("USD").provider(PaymentProvider.STRIPE)
                .status(PaymentStatus.REFUND_REQUESTED).build();
        refund = PaymentRefund.builder().id(UUID.randomUUID()).payment(payment).amount(new BigDecimal("100.00"))
                .status(RefundStatus.REFUND_REQUESTED).providerIdempotencyKey("durable-provider-key")
                .leaseToken(UUID.randomUUID()).leaseUntil(Instant.now().plusSeconds(120)).attemptCount(1)
                .firstProviderAttemptAt(Instant.now()).build();
        work = snapshot(null, Instant.now());
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.of(payment));
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        when(refunds.findByPayment_IdOrderByCreatedAtDesc(payment.getId())).thenReturn(List.of(refund));
    }

    @Test void fullRefundIsCountedOnceAndPublishesAtomicOutcome() {
        workflow.complete(work, new RefundGatewayResponse(true, "re_paid", "succeeded", null));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        verify(publisher).publishRefundCompleted(payment, refund, new BigDecimal("100.00"));
        workflow.complete(work, new RefundGatewayResponse(true, "re_paid", "succeeded", null));
        verifyNoMoreInteractions(publisher);
    }

    @Test void exhaustedUncertainAcceptanceIsManualReviewAndKeepsFundsReserved() {
        ReflectionTestUtils.setField(workflow, "maxAttempts", 1);
        workflow.retry(work);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_MANUAL_REVIEW);
        assertThat(refund.getProviderIdempotencyKey()).isEqualTo("durable-provider-key");
        assertThat(refund.getLeaseToken()).isNull();
        verify(publisher).publishRefundFailed(payment, refund);
    }

    @Test void staleLeaseAndLateFailureCannotOverwriteCompletedRefund() {
        refund.setLeaseToken(UUID.randomUUID());
        workflow.complete(work, new RefundGatewayResponse(true, "re_other", "succeeded", null));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_REQUESTED);
        verifyNoInteractions(publisher);
    }

    @Test void acceptedPendingRefundUsesStatusLookupOnNextAttempt() {
        workflow.complete(work, new RefundGatewayResponse(true, "re_pending", "pending", null));
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_PROCESSING);
        assertThat(refund.getNextAttemptAt()).isAfter(Instant.now());
        verifyNoInteractions(publisher);
        PaymentRefundWorkflow transactions = mock(PaymentRefundWorkflow.class);
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        RefundWork pending = snapshot("re_pending", Instant.now());
        when(transactions.claim(1)).thenReturn(List.of(pending), List.of());
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        RefundGatewayResponse success = new RefundGatewayResponse(true, "re_pending", "succeeded", null);
        when(gateway.retrieveRefund("re_pending")).thenReturn(success);
        new PaymentRefundWorker(transactions, factory).run();
        verify(gateway).retrieveRefund("re_pending");
        verify(gateway, never()).refund(any());
        verify(transactions).complete(pending, success);
    }

    @Test void timeoutAfterAcceptanceReplaysExactlyThePersistedProviderKey() {
        PaymentRefundWorkflow transactions = mock(PaymentRefundWorkflow.class);
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(transactions.claim(1)).thenReturn(List.of(work), List.of(), List.of(work), List.of());
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        when(gateway.refund(any())).thenThrow(new RuntimeException("timeout"))
                .thenReturn(new RefundGatewayResponse(true, "re_once", "succeeded", null));
        PaymentRefundWorker worker = new PaymentRefundWorker(transactions, factory);
        worker.run(); worker.run();
        var commands = org.mockito.ArgumentCaptor.forClass(RefundGatewayRequest.class);
        verify(gateway, times(2)).refund(commands.capture());
        assertThat(commands.getAllValues()).extracting(RefundGatewayRequest::idempotencyKey)
                .containsExactly("durable-provider-key", "durable-provider-key");
        verify(transactions).retry(work);
    }

    @Test void expiredProviderIdempotencyWindowNeverCreatesAnotherRefund() {
        PaymentRefundWorkflow transactions = mock(PaymentRefundWorkflow.class);
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        RefundWork old = snapshot(null, Instant.now().minusSeconds(24 * 3600));
        when(transactions.claim(1)).thenReturn(List.of(old), List.of());
        new PaymentRefundWorker(transactions, factory).run();
        verify(transactions).manualReview(old);
        verifyNoInteractions(factory);
    }

    private RefundWork snapshot(String providerId, Instant firstAttempt) {
        return new RefundWork(refund.getId(), payment.getId(), payment.getOrderId(), PaymentProvider.STRIPE,
                refund.getAmount(), "USD", "reason", "pi_paid", providerId,
                refund.getProviderIdempotencyKey(), refund.getLeaseToken(), firstAttempt);
    }
}
