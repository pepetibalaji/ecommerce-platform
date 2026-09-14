package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.webhook.VerifiedWebhookProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRefundWebhookSafetyTest {
    @Test void successAfterDefinitiveRefundFailureRaisesReviewWithoutReleasingOrChangingFunds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentRefundRepository refunds = mock(PaymentRefundRepository.class);
        PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
        var processor = new VerifiedWebhookProcessor(jdbc, new ObjectMapper(), payments,
                mock(PaymentAttemptRepository.class), refunds, publisher,
                mock(EntityManager.class), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(processor, "maxAttempts", 12);
        UUID eventId = UUID.randomUUID();
        Payment payment = Payment.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("USD").provider(PaymentProvider.STRIPE)
                .status(PaymentStatus.SUCCESS).build();
        PaymentRefund refund = PaymentRefund.builder().id(UUID.randomUUID()).payment(payment)
                .providerRefundId("re_failed").status(RefundStatus.REFUND_FAILED).amount(payment.getAmount()).build();
        when(jdbc.queryForList(anyString(), eq(eventId))).thenReturn(List.of(Map.of(
                "processing_status", "RECEIVED", "attempts", 0, "provider", "STRIPE",
                "verified_metadata", "{\"providerRefundId\":\"re_failed\",\"refundStatus\":\"SUCCESS\"}")));
        when(jdbc.queryForObject(anyString(), eq(UUID.class), eq(refund.getId()))).thenReturn(payment.getOrderId());
        when(refunds.findByProviderRefundId("re_failed")).thenReturn(Optional.of(refund));
        when(payments.findByOrderIdForUpdate(payment.getOrderId())).thenReturn(Optional.of(payment));
        when(refunds.findByIdForUpdate(refund.getId())).thenReturn(Optional.of(refund));
        assertThat(processor.process(eventId)).isEqualTo(WebhookProcessingStatus.FAILED);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REFUND_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(jdbc).update(contains("last_error_code"), eq(payment.getId()), eq(12),
                eq("LATE_REFUND_SUCCESS_REQUIRES_REVIEW"), eq(eventId));
        verifyNoInteractions(publisher);
    }
}
