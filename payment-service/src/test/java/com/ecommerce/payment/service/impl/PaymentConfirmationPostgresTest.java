package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.kafka.producer.*;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.outbox.*;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.service.PaymentWebhookService;
import com.ecommerce.payment.webhook.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.cloud.config.enabled=false","spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentWebhookServiceImpl.class,VerifiedWebhookInbox.class,VerifiedWebhookProcessor.class,
        PaymentOutboxStore.class,KafkaPaymentEventPublisher.class,PaymentConfirmationPostgresTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentConfirmationPostgresTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired PaymentWebhookService service;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired PaymentRefundRepository refunds;
    @Autowired VerifiedWebhookProcessor processor;
    @Autowired VerifiedWebhookInbox inbox;
    @Autowired PaymentOutboxStore outbox;
    @Autowired PaymentEventPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper mapper;
    @Autowired MeterRegistry metrics;
    @MockBean PaymentGatewayFactory factory;
    PaymentGateway stripe;
    Payment payment;
    PaymentAttempt attempt;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE payment_event_outbox,payment_webhook_events,payment_cancellation_requests,payment_refunds,payment_attempts,payments CASCADE");
        stripe=mock(PaymentGateway.class);when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(stripe);
        payment=payments.save(Payment.builder().orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("599.00")).currency("INR").provider(PaymentProvider.STRIPE)
                .idempotencyKey(UUID.randomUUID().toString()).status(PaymentStatus.REQUIRES_CUSTOMER_ACTION).build());
        attempt=attempts.save(PaymentAttempt.builder().payment(payment).provider(PaymentProvider.STRIPE)
                .idempotencyKey("checkout:"+UUID.randomUUID()).providerSessionId("cs_test_saved")
                .expiresAt(Instant.now().plusSeconds(300)).status(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION).build());
    }
    ProviderWebhookEvent event(String id,ProviderPaymentStatus status) {
        return ProviderWebhookEvent.builder().provider(PaymentProvider.STRIPE).providerEventId(id).eventType("checkout.session.completed")
                .providerSessionId("cs_test_saved").providerPaymentIntentId("pi_test_saved").status(status).build();
    }
    void webhook(String id,ProviderPaymentStatus status) {
        when(stripe.parseWebhookEvent(id,"signature")).thenReturn(event(id,status));service.processWebhook(PaymentProvider.STRIPE,id,"signature");
    }
    long outboxCount() { return jdbc.queryForObject("SELECT count(*) FROM payment_event_outbox",Long.class); }
    PaymentStatus status() { return payments.findById(payment.getId()).orElseThrow().getStatus(); }

    @Test void independentWebhookReplicasShareDatabaseRateBudgetAndWindowRollover() {
        tx.executeWithoutResult(transaction -> {
            jdbc.execute("TRUNCATE payment_webhook_rate_limit");
            var firstReplica = new PaymentWebhookAdmissionFilter(jdbc);
            var secondReplica = new PaymentWebhookAdmissionFilter(jdbc);
            for (var filter : java.util.List.of(firstReplica, secondReplica)) {
                ReflectionTestUtils.setField(filter, "maxBodyBytes", 1024);
                ReflectionTestUtils.setField(filter, "stripeRate", 1);
            }
            var admitted = new java.util.concurrent.atomic.AtomicInteger();
            jakarta.servlet.FilterChain chain = (request, response) -> admitted.incrementAndGet();
            try {
                var firstRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/payments/webhooks/stripe");
                firstRequest.setContent(new byte[0]);
                var firstResponse = new org.springframework.mock.web.MockHttpServletResponse();
                firstReplica.doFilter(firstRequest, firstResponse, chain);
                var secondRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/payments/webhooks/stripe");
                secondRequest.setContent(new byte[0]);
                var secondResponse = new org.springframework.mock.web.MockHttpServletResponse();
                secondReplica.doFilter(secondRequest, secondResponse, chain);
                assertThat(admitted.get()).isEqualTo(1);
                assertThat(secondResponse.getStatus()).isEqualTo(429);
                assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("60");
                assertThat(jdbc.queryForObject("SELECT request_count FROM payment_webhook_rate_limit WHERE provider='STRIPE'", Integer.class))
                        .isEqualTo(2);
                // PostgreSQL now() is fixed within this transaction, making rollover deterministic.
                jdbc.update("UPDATE payment_webhook_rate_limit SET window_start=now()-interval '1 minute'");
                var afterRollover = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/payments/webhooks/stripe");
                afterRollover.setContent(new byte[0]);
                secondReplica.doFilter(afterRollover, new org.springframework.mock.web.MockHttpServletResponse(), chain);
                assertThat(admitted.get()).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT request_count FROM payment_webhook_rate_limit WHERE provider='STRIPE'", Integer.class))
                        .isEqualTo(1);
            } catch (java.io.IOException | jakarta.servlet.ServletException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test void refreshCannotConfirmAndOwnershipIsEnforced() {
        assertThat(service.refreshPayment(payment.getOrderId(),payment.getUserId()).getStatus()).isEqualTo(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
        assertThatThrownBy(()->service.refreshPayment(payment.getOrderId(),UUID.randomUUID()))
                .isInstanceOf(com.ecommerce.payment.exception.PaymentApiException.class);
        verifyNoInteractions(stripe);assertThat(outboxCount()).isZero();
    }
    @Test void verifiedSuccessAndDurableOutcomeAreAtomicAndDuplicatesCannotDowngrade() {
        webhook("paid",ProviderPaymentStatus.SUCCESS);webhook("paid",ProviderPaymentStatus.SUCCESS);webhook("late_failure",ProviderPaymentStatus.FAILED);
        assertThat(status()).isEqualTo(PaymentStatus.SUCCESS);assertThat(outboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload_hash FROM payment_webhook_events WHERE provider_event_id='paid'",String.class)).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT payload FROM payment_event_outbox",String.class)).contains(payment.getOrderId().toString(),"eventId","occurredAt");
    }
    @Test void rollbackCannotLeaveStateWithoutEvent() {
        tx.executeWithoutResult(t->{
            var p=payments.findByOrderIdForUpdate(payment.getOrderId()).orElseThrow();p.setStatus(PaymentStatus.SUCCESS);payments.saveAndFlush(p);
            publisher.publishPaymentSuccess(p);t.setRollbackOnly();
        });
        assertThat(status()).isEqualTo(PaymentStatus.REQUIRES_CUSTOMER_ACTION);assertThat(outboxCount()).isZero();
    }
    @Test void unresolvedVerifiedEventCanRecoverAfterAttemptIsMaterialized() {
        var unknown=event("early",ProviderPaymentStatus.SUCCESS).toBuilder().providerSessionId("cs_early").build();
        var receipt=inbox.accept(unknown,"signed early body");
        assertThat(processor.process(receipt.id())).isEqualTo(WebhookProcessingStatus.FAILED);
        attempt.setProviderSessionId("cs_early");attempts.save(attempt);
        assertThat(processor.process(receipt.id())).isEqualTo(WebhookProcessingStatus.PROCESSED);
        assertThat(status()).isEqualTo(PaymentStatus.SUCCESS);assertThat(outboxCount()).isEqualTo(1);
    }
    @Test void parallelVerifiedEventsProduceOneOrderFacingResult() throws Exception {
        var one=inbox.accept(event("evt_one",ProviderPaymentStatus.SUCCESS),"signed1");
        var two=inbox.accept(event("evt_two",ProviderPaymentStatus.SUCCESS),"signed2");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->processor.process(one.id()));var b=pool.submit(()->processor.process(two.id()));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(status()).isEqualTo(PaymentStatus.SUCCESS);assertThat(outboxCount()).isEqualTo(1);
    }
    @Test void expiryIsDurableAndLateSuccessRequiresManualReview() {
        attempt.setExpiresAt(Instant.now().minusSeconds(1));attempts.save(attempt);
        new PaymentReconciliationWorker(jdbc,tx,payments,attempts,refunds,publisher,metrics).expire();
        webhook("late_paid",ProviderPaymentStatus.SUCCESS);
        assertThat(status()).isEqualTo(PaymentStatus.EXPIRED);assertThat(outboxCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT topic FROM payment_event_outbox",String.class)).isEqualTo("payment-expired");
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM payment_webhook_events WHERE provider_event_id='late_paid'",String.class))
                .isEqualTo("LATE_SUCCESS_REQUIRES_REVIEW");
    }
    @Test void kafkaOutageRetainsEventAndRetryDeliversSameIdAndOrderKey() throws Exception {
        webhook("paid",ProviderPaymentStatus.SUCCESS);
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> kafka=mock(KafkaTemplate.class);
        var worker=new PaymentOutboxWorker(jdbc,tx,kafka,mapper,metrics);
        ReflectionTestUtils.setField(worker,"batchSize",1);ReflectionTestUtils.setField(worker,"maxAttempts",2);ReflectionTestUtils.setField(worker,"sendTimeoutSeconds",1);
        when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka down")));
        worker.deliver();
        UUID eventId=jdbc.queryForObject("SELECT id FROM payment_event_outbox",UUID.class);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_event_outbox",String.class)).isEqualTo("PENDING");
        jdbc.update("UPDATE payment_event_outbox SET next_attempt_at=now()");
        when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.completedFuture(null));worker.deliver();
        assertThat(jdbc.queryForObject("SELECT status FROM payment_event_outbox",String.class)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT id FROM payment_event_outbox",UUID.class)).isEqualTo(eventId);
        verify(kafka,times(2)).send(eq("payment-success"),eq(payment.getOrderId().toString()),any());
    }
    @Test void terminalOutboxFailureRequiresExplicitReplayAndExpiredLeaseRecovers() {
        webhook("paid",ProviderPaymentStatus.SUCCESS);
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> kafka=mock(KafkaTemplate.class);
        var worker=new PaymentOutboxWorker(jdbc,tx,kafka,mapper,metrics);
        ReflectionTestUtils.setField(worker,"batchSize",1);ReflectionTestUtils.setField(worker,"maxAttempts",1);ReflectionTestUtils.setField(worker,"sendTimeoutSeconds",1);
        jdbc.update("UPDATE payment_event_outbox SET status='LEASED',lease_token=?,lease_until=now()-interval '1 minute'",UUID.randomUUID());
        when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));worker.deliver();worker.deliver();
        assertThat(jdbc.queryForObject("SELECT status FROM payment_event_outbox",String.class)).isEqualTo("DEAD");verify(kafka,times(1)).send(anyString(),anyString(),any());
    }
    @Test void reconciliationRepairsMissingLegacyOutcomeIdempotently() {
        payment.setStatus(PaymentStatus.SUCCESS);payments.save(payment);
        var worker=new PaymentReconciliationWorker(jdbc,tx,payments,attempts,refunds,publisher,metrics);
        worker.reconcile();worker.reconcile();assertThat(outboxCount()).isEqualTo(1);
    }
    @Test void inboxSurvivesFailureOfAtomicStateAndOutboxTransaction() {
        jdbc.execute("ALTER TABLE payment_event_outbox ADD CONSTRAINT test_reject_success CHECK (topic <> 'payment-success')");
        try {
            webhook("persisted_before_failure",ProviderPaymentStatus.SUCCESS);
            assertThat(status()).isEqualTo(PaymentStatus.REQUIRES_CUSTOMER_ACTION);
            assertThat(outboxCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_webhook_events WHERE verified_metadata IS NOT NULL",Long.class)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE payment_event_outbox DROP CONSTRAINT test_reject_success"); }
        UUID id=jdbc.queryForObject("SELECT id FROM payment_webhook_events",UUID.class);
        assertThat(processor.process(id)).isEqualTo(WebhookProcessingStatus.PROCESSED);
        assertThat(status()).isEqualTo(PaymentStatus.SUCCESS);assertThat(outboxCount()).isEqualTo(1);
    }
    @Test void concurrentOutboxWorkersDoNotShareAnUnexpiredLease() throws Exception {
        webhook("paid",ProviderPaymentStatus.SUCCESS);
        @SuppressWarnings("unchecked") KafkaTemplate<String,Object> kafka=mock(KafkaTemplate.class);
        var worker=new PaymentOutboxWorker(jdbc,tx,kafka,mapper,metrics);
        ReflectionTestUtils.setField(worker,"batchSize",1);ReflectionTestUtils.setField(worker,"maxAttempts",2);ReflectionTestUtils.setField(worker,"sendTimeoutSeconds",10);
        var started=new CountDownLatch(1);
        var acknowledgement=new CompletableFuture<org.springframework.kafka.support.SendResult<String,Object>>();
        when(kafka.send(anyString(),anyString(),any())).thenAnswer(invocation->{started.countDown();return acknowledgement;});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(worker::deliver);
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            pool.submit(worker::deliver).get(5,TimeUnit.SECONDS);
            verify(kafka,times(1)).send(anyString(),anyString(),any());
            acknowledgement.complete(null);first.get(5,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM payment_event_outbox",String.class)).isEqualTo("DELIVERED");
    }
    @TestConfiguration static class Beans {
        @Bean PaymentMapper paymentMapper() { return org.mapstruct.factory.Mappers.getMapper(PaymentMapper.class); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean PaymentMetrics paymentMetrics(MeterRegistry registry) { return new PaymentMetrics(registry); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
}
