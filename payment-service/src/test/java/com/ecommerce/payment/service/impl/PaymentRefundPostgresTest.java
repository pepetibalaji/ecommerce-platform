package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.kafka.producer.PaymentEventPublisher;
import com.ecommerce.payment.mapper.PaymentRefundMapper;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.*;
import com.ecommerce.payment.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises actual PostgreSQL parent locks, committed work, leases, and outcome rollback. */
@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentRefundServiceImpl.class, PaymentRefundWorkflow.class, PaymentRefundPostgresTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class PaymentRefundPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired PaymentRefundService service;
    @Autowired PaymentRefundWorkflow workflow;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired PaymentRefundRepository refunds;
    @MockBean PaymentEventPublisher publisher;
    Payment payment;

    @BeforeEach void setup() {
        refunds.deleteAll(); attempts.deleteAll(); payments.deleteAll();
        payment = payments.save(Payment.builder().orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("USD").provider(PaymentProvider.STRIPE)
                .status(PaymentStatus.SUCCESS).idempotencyKey(UUID.randomUUID().toString()).build());
        attempts.save(PaymentAttempt.builder().payment(payment).provider(PaymentProvider.STRIPE)
                .providerPaymentIntentId("pi_" + UUID.randomUUID()).idempotencyKey(UUID.randomUUID().toString())
                .status(PaymentAttemptStatus.SUCCESS).build());
    }

    @Test void concurrentRequestsCannotOverReserveThePayment() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> request = () -> {
                start.await();
                try { refund("75.00", UUID.randomUUID().toString()); return true; }
                catch (PaymentApiException refused) { return false; }
            };
            Future<Boolean> one = workers.submit(request); Future<Boolean> two = workers.submit(request);
            start.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(refunds.findAll()).singleElement().satisfies(r ->
                assertThat(r.getAmount()).isEqualByComparingTo("75.00"));
    }

    @Test void timeoutAfterAcceptanceReplaysPersistedKeyAndCreatesOneProviderRefund() {
        var created = refund("100.00", "timeout-case");
        PaymentGatewayFactory factory = mock(PaymentGatewayFactory.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(factory.getGateway(PaymentProvider.STRIPE)).thenReturn(gateway);
        Map<String, RefundGatewayResponse> providerLedger = new ConcurrentHashMap<>();
        AtomicBoolean first = new AtomicBoolean(true);
        when(gateway.refund(any())).thenAnswer(call -> {
            RefundGatewayRequest request = call.getArgument(0);
            var result = providerLedger.computeIfAbsent(request.idempotencyKey(),
                    key -> new RefundGatewayResponse(true, "re_accepted", "succeeded", null));
            if (first.getAndSet(false)) throw new IllegalStateException("timeout after acceptance");
            return result;
        });
        PaymentRefundWorker worker = new PaymentRefundWorker(workflow, factory);
        worker.run();
        PaymentRefund queued = refunds.findById(created.refundId()).orElseThrow();
        assertThat(queued.getStatus()).isEqualTo(RefundStatus.REFUND_REQUESTED);
        assertThat(queued.getAttemptCount()).isEqualTo(1);
        queued.setNextAttemptAt(Instant.now().minusSeconds(1)); refunds.save(queued);
        worker.run();
        assertThat(providerLedger).hasSize(1);
        assertThat(refunds.findById(created.refundId()).orElseThrow().getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(publisher).publishRefundCompleted(any(), any(), eq(new BigDecimal("100.00")));
    }

    @Test void onlyOneWorkerClaimsARefundAndFailedOutcomePersistenceRollsBackStatus() {
        var created = refund("100.00", "atomic-case");
        RefundWork work = workflow.claim(1).getFirst();
        assertThat(workflow.claim(1)).isEmpty();
        doThrow(new IllegalStateException("outbox storage unavailable"))
                .when(publisher).publishRefundCompleted(any(), any(), any());
        assertThatThrownBy(() -> workflow.complete(work,
                new RefundGatewayResponse(true, "re_atomic", "succeeded", null))).isInstanceOf(IllegalStateException.class);
        PaymentRefund stored = refunds.findById(created.refundId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RefundStatus.REFUND_REQUESTED);
        assertThat(stored.getProviderRefundId()).isNull();
        assertThat(stored.getLeaseToken()).isEqualTo(work.leaseToken());
    }

    private com.ecommerce.payment.dto.response.AdminRefundResponse refund(String amount, String key) {
        return service.refundPayment(payment.getId(), payment.getOrderId(), new BigDecimal(amount), "USD", "reason", key);
    }

    @TestConfiguration static class Beans {
        @Bean PaymentRefundMapper refundMapper() { return org.mapstruct.factory.Mappers.getMapper(PaymentRefundMapper.class); }
        @Bean MeterRegistry registry() { return new SimpleMeterRegistry(); }
        @Bean PaymentMetrics metrics(MeterRegistry registry) { return new PaymentMetrics(registry); }
    }
}