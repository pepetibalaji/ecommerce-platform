package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.mapper.PaymentRefundMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.service.RefundAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundServiceImplTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    PaymentRefundServiceImpl service = new PaymentRefundServiceImpl(refunds, payments, attempts,
            mock(PaymentRefundMapper.class), mock(PaymentMetrics.class));
    Payment payment;
    List<PaymentRefund> stored;

    @BeforeEach void setup() {
        payment = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .status(PaymentStatus.SUCCESS).provider(PaymentProvider.STRIPE)
                .amount(new BigDecimal("100.00")).currency("USD").build();
        stored = new ArrayList<>();
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.of(payment));
        when(refunds.findByPayment_IdOrderByCreatedAtDesc(payment.getId())).thenAnswer(call -> stored);
        when(attempts.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(eq(payment.getId()), any()))
                .thenReturn(Optional.of(PaymentAttempt.builder().status(PaymentAttemptStatus.SUCCESS)
                        .providerPaymentIntentId("pi_paid").build()));
        when(refunds.saveAndFlush(any())).thenAnswer(call -> {
            PaymentRefund refund = call.getArgument(0); refund.setId(UUID.randomUUID()); stored.add(refund); return refund;
        });
    }

    @Test void durableCommandIsSavedWithAuditAndIndependentProviderKey() {
        UUID actor = UUID.randomUUID(); UUID command = UUID.randomUUID(); Instant requested = Instant.now().minusSeconds(5);
        var result = service.refundPayment(payment.getId(), payment.getOrderId(), new BigDecimal("40.00"),
                "USD", "customer request", "request-1", new RefundAudit(command, actor, "CUSTOMER", "correlation", "trace", requested));
        assertThat(result.status()).isEqualTo("REFUND_REQUESTED");
        assertThat(stored).singleElement().satisfies(refund -> {
            assertThat(refund.getProviderIdempotencyKey()).startsWith("refund:").isNotEqualTo("request-1");
            assertThat(refund.getProviderPaymentIntentId()).isEqualTo("pi_paid");
            assertThat(refund.getRequestedBy()).isEqualTo(actor);
            assertThat(refund.getRefundRequestId()).isEqualTo(command);
            assertThat(refund.getRequestedAt()).isEqualTo(requested);
            assertThat(refund.getCorrelationId()).isEqualTo("correlation");
        });
        verify(payments).findByOrderIdForUpdate(payment.getOrderId());
    }

    @Test void pendingAndUnknownAcceptedRefundsReserveFunds() {
        stored.add(PaymentRefund.builder().amount(new BigDecimal("50.00")).status(RefundStatus.REFUND_PROCESSING).build());
        stored.add(PaymentRefund.builder().amount(new BigDecimal("30.00")).status(RefundStatus.REFUND_MANUAL_REVIEW).build());
        assertThatThrownBy(() -> request("30.00", "over-budget")).isInstanceOf(PaymentApiException.class);
        verify(refunds, never()).saveAndFlush(any());
    }

    @Test void duplicateCommandReturnsSameRefundButChangedPayloadIsRejected() {
        request("40.00", "same-key");
        PaymentRefund first = stored.getFirst();
        when(refunds.findByIdempotencyKey("same-key")).thenReturn(Optional.of(first));
        assertThat(request("40.00", "same-key").refundId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> request("41.00", "same-key")).isInstanceOf(PaymentApiException.class);
        verify(refunds, times(1)).saveAndFlush(any());
    }

    @Test void partialRefundRequestsReserveRemainingBudgetExactly() {
        request("40.00", "partial-1");
        request("60.00", "partial-2");
        assertThatThrownBy(() -> request("0.01", "partial-3")).isInstanceOf(PaymentApiException.class);
        assertThat(stored).hasSize(2);
    }

    @Test void operatorReconciliationPreservesProviderIdentityAndOriginalAudit() {
        request("40.00", "manual-case");
        PaymentRefund refund = stored.getFirst();
        refund.setStatus(RefundStatus.REFUND_MANUAL_REVIEW);
        refund.setProviderRefundId("re_known");
        refund.setFirstProviderAttemptAt(Instant.now().minusSeconds(3 * 24 * 3600));
        Instant originalAttempt = refund.getFirstProviderAttemptAt();
        String originalKey = refund.getProviderIdempotencyKey();
        UUID originalActor = UUID.randomUUID(); refund.setRequestedBy(originalActor);
        when(refunds.findOrderId(payment.getId(), refund.getId())).thenReturn(Optional.of(payment.getOrderId()));
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        UUID operator = UUID.randomUUID();
        service.reconcileRefund(payment.getId(), refund.getId(), operator, "provider outage resolved");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_PROCESSING);
        assertThat(refund.getProviderIdempotencyKey()).isEqualTo(originalKey);
        assertThat(refund.getFirstProviderAttemptAt()).isEqualTo(originalAttempt);
        assertThat(refund.getRequestedBy()).isEqualTo(originalActor);
        assertThat(refund.getLastReconciledBy()).isEqualTo(operator);
        assertThat(refund.getReconciliationCount()).isEqualTo(1);
    }

    @Test void operatorCannotReplayUnknownAcceptanceOutsideProviderKeyRetention() {
        request("40.00", "unknown-case");
        PaymentRefund refund = stored.getFirst();
        refund.setStatus(RefundStatus.REFUND_MANUAL_REVIEW);
        refund.setFirstProviderAttemptAt(Instant.now().minusSeconds(24 * 3600));
        when(refunds.findOrderId(payment.getId(), refund.getId())).thenReturn(Optional.of(payment.getOrderId()));
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        assertThatThrownBy(() -> service.reconcileRefund(payment.getId(), refund.getId(), UUID.randomUUID(), "retry"))
                .isInstanceOf(PaymentApiException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_MANUAL_REVIEW);
    }
    private com.ecommerce.payment.dto.response.AdminRefundResponse request(String amount, String key) {
        return service.refundPayment(payment.getId(), payment.getOrderId(), new BigDecimal(amount), "USD", "reason", key);
    }
}
