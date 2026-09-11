package com.ecommerce.payment.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.exception.PaymentConfirmationUnavailableException;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.service.PaymentWebhookService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentWebhookServiceImpl.class, PaymentConfirmationPostgresTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentConfirmationPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired PaymentWebhookService service;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired PaymentWebhookEventRepository webhooks;
    @Autowired PaymentRefundRepository refunds;
    @MockBean PaymentGatewayFactory factory;
    @MockBean PaymentEventPublisher publisher;
    PaymentGateway stripe;
    Payment payment;
    @BeforeEach void fixtures() {
        webhooks.deleteAll(); refunds.deleteAll(); attempts.deleteAll(); payments.deleteAll();
        stripe = mock(PaymentGateway.class);
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(stripe);
        payment = payments.save(Payment.builder().orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("599.00")).currency("INR").provider(PaymentProvider.STRIPE)
                .idempotencyKey(UUID.randomUUID().toString()).status(PaymentStatus.REQUIRES_CUSTOMER_ACTION).build());
        attempts.save(PaymentAttempt.builder().payment(payment).provider(PaymentProvider.STRIPE)
                .providerSessionId("cs_test_saved").status(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION).build());
    }
    ProviderWebhookEvent event(String id, ProviderPaymentStatus status) {
        return ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE).providerEventId(id)
                .eventType("checkout.session.completed").providerSessionId("cs_test_saved")
                .providerPaymentIntentId("pi_test_saved").status(status).build();
    }
    @Test void missingWebhookIsRecoveredAndLateEventsCannotDowngradeOrRepublish() {
        when(stripe.getPaymentStatus("cs_test_saved")).thenReturn(event("lookup", ProviderPaymentStatus.SUCCESS));
        assertThat(service.refreshPayment(payment.getOrderId(), payment.getUserId()).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        service.refreshPayment(payment.getOrderId(), payment.getUserId());
        when(stripe.parseWebhookEvent("late", "signature")).thenReturn(event("late-failure", ProviderPaymentStatus.FAILED));
        service.processWebhook(PaymentProvider.STRIPE, "late", "signature");
        when(stripe.parseWebhookEvent("paid", "signature")).thenReturn(event("paid", ProviderPaymentStatus.SUCCESS));
        service.processWebhook(PaymentProvider.STRIPE, "paid", "signature");
        assertThat(service.processWebhook(PaymentProvider.STRIPE, "paid", "signature").isDuplicate()).isTrue();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(stripe, times(1)).getPaymentStatus("cs_test_saved");
        verify(publisher, times(1)).publishPaymentSuccess(any());
        verify(publisher, never()).publishPaymentFailed(any());
    }
    @Test void wrongOwnerCannotTriggerProviderLookup() {
        assertThatThrownBy(() -> service.refreshPayment(payment.getOrderId(), UUID.randomUUID()))
                .isInstanceOf(com.ecommerce.common.exception.ResourceNotFoundException.class);
        verifyNoInteractions(stripe, publisher);
    }
    @Test void providerOutageRollsBackWithoutInventingPaymentResult() {
        when(stripe.getPaymentStatus("cs_test_saved")).thenThrow(new PaymentConfirmationUnavailableException());
        assertThatThrownBy(() -> service.refreshPayment(payment.getOrderId(), payment.getUserId()))
                .isInstanceOf(PaymentConfirmationUnavailableException.class);
        var stored = payments.findById(payment.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        assertThat(stored.getLastProviderCheckAt()).isNull();
        verifyNoInteractions(publisher);
    }
    @Test void openSessionIsNotSuccessAndRepeatedChecksAreThrottled() {
        when(stripe.getPaymentStatus("cs_test_saved")).thenReturn(event("open", ProviderPaymentStatus.IGNORED));
        service.refreshPayment(payment.getOrderId(), payment.getUserId());
        service.refreshPayment(payment.getOrderId(), payment.getUserId());
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        verify(stripe, times(1)).getPaymentStatus("cs_test_saved");
        verifyNoInteractions(publisher);
    }
    @Test void wrongSessionResponseFailsClosed() {
        when(stripe.getPaymentStatus("cs_test_saved")).thenReturn(event("other", ProviderPaymentStatus.SUCCESS)
                .toBuilder().providerSessionId("cs_other").build());
        assertThatThrownBy(() -> service.refreshPayment(payment.getOrderId(), payment.getUserId()))
                .isInstanceOf(PaymentConfirmationUnavailableException.class);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        verifyNoInteractions(publisher);
    }
    @Test void concurrentRefreshesOnlyPublishOneOutcome() throws Exception {
        when(stripe.getPaymentStatus("cs_test_saved")).thenReturn(event("lookup", ProviderPaymentStatus.SUCCESS));
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.refreshPayment(payment.getOrderId(), payment.getUserId()));
            var second = workers.submit(() -> service.refreshPayment(payment.getOrderId(), payment.getUserId()));
            assertThat(first.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(second.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }
        verify(stripe, times(1)).getPaymentStatus("cs_test_saved");
        verify(publisher, times(1)).publishPaymentSuccess(any());
    }
    @TestConfiguration static class Beans {
        @Bean PaymentMapper mapper() { return org.mapstruct.factory.Mappers.getMapper(PaymentMapper.class); }
        @Bean PaymentMetrics metrics() { return new PaymentMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()); }
    }
}
