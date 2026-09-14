package com.ecommerce.payment.service.impl;

import com.ecommerce.common.events.payment.PaymentCancellationRequestedEvent;
import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCancellationServiceTest {
    PaymentCancellationRequestRepository requests = mock(PaymentCancellationRequestRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    PaymentRefundService refundService = mock(PaymentRefundService.class);
    PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    PaymentCancellationPreparationService preparation = mock(PaymentCancellationPreparationService.class);
    PaymentCancellationService service = new PaymentCancellationService(requests, payments, attempts, refunds,
            refundService, publisher, new SimpleMeterRegistry(), preparation);
    Payment payment = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).userId(UUID.randomUUID())
            .amount(new BigDecimal("100.00")).currency("USD").status(PaymentStatus.PENDING).build();
    PaymentCancellationRequest request = PaymentCancellationRequest.builder().id(UUID.randomUUID())
            .orderId(payment.getOrderId()).userId(payment.getUserId()).amount(payment.getAmount())
            .currency("USD").status("REQUESTED").requestedBy(payment.getUserId()).actorType("CUSTOMER")
            .reason("cancel").receivedAt(Instant.now()).requestedAt(Instant.now()).build();

    @Test void cancellationOvertakingPreparationIsDurablyRecordedAndIdempotent() {
        var event = new PaymentCancellationRequestedEvent(request.getId(), payment.getOrderId(), payment.getUserId(),
                payment.getUserId(), "CUSTOMER", payment.getAmount(), "USD", "cancel", Instant.now(), "corr", "trace");
        service.enqueue(event);
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        service.enqueue(event);
        verify(requests, times(1)).saveAndFlush(any());
        when(requests.findNextForUpdate(any())).thenReturn(List.of(request));
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.empty());
        service.processNext();
        assertThat(request.getStatus()).isEqualTo("WAITING_PROVIDER");
        verifyNoInteractions(publisher, refundService);
    }

    @Test void unpaidCancellationClosesAttemptAndQueuesOutcome() {
        due();
        PaymentAttempt attempt = PaymentAttempt.builder().status(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION).build();
        when(attempts.findByPayment_IdOrderByCreatedAtDesc(payment.getId())).thenReturn(List.of(attempt));
        service.processNext();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CANCELLED);
        assertThat(request.getStatus()).isEqualTo("COMPLETED");
        verify(publisher).publishPaymentFailed(payment);
        verifyNoInteractions(refundService);
    }

    @Test void processingPaymentWaitsForVerifiedOutcomeBeforeCancelling() {
        due(); payment.setStatus(PaymentStatus.PROCESSING);
        service.processNext();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(request.getStatus()).isEqualTo("WAITING_PROVIDER");
        verifyNoInteractions(publisher, refundService);
    }

    @Test void paidCancellationQueuesFullRemainingRefundWithOriginalAudit() {
        due(); payment.setStatus(PaymentStatus.SUCCESS);
        when(refunds.findByPayment_IdOrderByCreatedAtDesc(payment.getId())).thenReturn(List.of(
                PaymentRefund.builder().amount(new BigDecimal("40.00")).status(RefundStatus.REFUNDED).build()));
        service.processNext();
        var audit = org.mockito.ArgumentCaptor.forClass(RefundAudit.class);
        verify(refundService).refundPayment(eq(payment.getId()), eq(payment.getOrderId()), eq(new BigDecimal("60.00")),
                eq("USD"), eq("cancel"), eq("order-refund-request:" + request.getId()), audit.capture());
        assertThat(audit.getValue().requestedBy()).isEqualTo(payment.getUserId());
        assertThat(audit.getValue().refundRequestId()).isEqualTo(request.getId());
        assertThat(request.getStatus()).isEqualTo("COMPLETED");
    }

    @Test void expiryRacingWithVerifiedSuccessDoesNotRefundAPaidOrder() {
        due(); request.setExpiryRequested(true); payment.setStatus(PaymentStatus.SUCCESS);
        service.processNext();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(publisher).publishPaymentSuccess(payment);
        verifyNoInteractions(refundService);
    }

    @Test void missingPaymentIsRecreatedFromTrustedCommandThenCancelled() {
        when(requests.findNextForUpdate(any())).thenReturn(List.of(request));
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.empty(), Optional.of(payment));
        service.processNext();
        verify(preparation).prepare(request);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(request.getStatus()).isEqualTo("COMPLETED");
        verify(publisher).publishPaymentFailed(payment);
    }

    @Test void failedTrustedPreparationKeepsDurableBackoffInsteadOfAcknowledgingCancellation() {
        when(requests.findNextForUpdate(any())).thenReturn(List.of(request));
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("lookup unavailable")).when(preparation).prepare(request);
        service.processNext();
        assertThat(request.getStatus()).isEqualTo("WAITING_PROVIDER");
        assertThat(request.getAttemptCount()).isEqualTo(1);
        assertThat(request.getNextAttemptAt()).isAfter(Instant.now());
        verifyNoInteractions(publisher, refundService);
    }
    private void due() {
        when(requests.findNextForUpdate(any())).thenReturn(List.of(request));
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.of(payment));
    }
}