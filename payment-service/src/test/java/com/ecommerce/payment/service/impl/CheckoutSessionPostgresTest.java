package com.ecommerce.payment.service.impl;

import com.ecommerce.payment.config.*;
import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.observability.PaymentMetrics;
import com.ecommerce.payment.order.TrustedOrderClient;
import com.ecommerce.payment.provider.*;
import com.ecommerce.payment.provider.model.*;
import com.ecommerce.payment.repository.*;
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
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CheckoutSessionTransactions.class, PaymentCheckoutServiceImpl.class, CheckoutUrlPolicy.class,
        CheckoutSessionPostgresTest.Beans.class})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class CheckoutSessionPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired PaymentCheckoutServiceImpl checkout;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @MockBean PaymentGatewayFactory gateways;
    @MockBean TrustedOrderClient trustedOrders;
    PaymentGateway provider;
    Payment payment;

    @BeforeEach void setUp() {
        payment = payments.saveAndFlush(Payment.builder().orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("10.00")).currency("USD").provider(PaymentProvider.SANDBOX)
                .status(PaymentStatus.PENDING).idempotencyKey(UUID.randomUUID().toString()).build());
        provider = mock(PaymentGateway.class);
        when(gateways.getActiveGateway()).thenReturn(provider);
        when(gateways.getGateway(PaymentProvider.SANDBOX)).thenReturn(provider);
    }

    @Test void concurrentTabsCreateAtMostOneProviderSession() throws Exception {
        when(provider.createCheckoutSession(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return checkout.createCheckoutSession(payment.getOrderId(), payment.getUserId()); });
            var second = executor.submit(() -> { start.await(); return checkout.createCheckoutSession(payment.getOrderId(), payment.getUserId()); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).getCheckoutUrl()).isEqualTo(second.get(15, TimeUnit.SECONDS).getCheckoutUrl());
        }
        assertThat(attempts.findByPayment_IdOrderByCreatedAtDesc(payment.getId())).hasSize(1);
        verify(provider, times(1)).createCheckoutSession(any());
    }

    @Test void providerAcceptanceThenLocalCommitFailureReplaysDurableReservation() {
        AtomicBoolean failCommit = new AtomicBoolean(true);
        Set<String> providerSessions = new HashSet<>();
        when(provider.createCheckoutSession(any())).thenAnswer(invocation -> {
            CreateCheckoutSessionCommand command = invocation.getArgument(0);
            providerSessions.add(command.getIdempotencyKey());
            if (failCommit.getAndSet(false)) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("simulated database commit failure");
                    }
                });
            }
            return result(command);
        });
        assertThatThrownBy(() -> checkout.createCheckoutSession(payment.getOrderId(), payment.getUserId()))
                .isInstanceOf(IllegalStateException.class);
        PaymentAttempt reserved = attempts.findTopByPayment_IdOrderByCreatedAtDesc(payment.getId()).orElseThrow();
        assertThat(reserved.getStatus()).isEqualTo(PaymentAttemptStatus.CREATED);
        assertThat(reserved.getProviderSessionId()).isNull();
        assertThat(reserved.getIdempotencyKey()).isNotBlank();
        checkout.createCheckoutSession(payment.getOrderId(), payment.getUserId());
        assertThat(providerSessions).containsExactly(reserved.getIdempotencyKey());
        assertThat(attempts.findByPayment_IdOrderByCreatedAtDesc(payment.getId())).singleElement()
                .satisfies(attempt -> assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.REQUIRES_CUSTOMER_ACTION));
        verify(provider, times(2)).createCheckoutSession(any());
    }

    private CheckoutSessionResult result(CreateCheckoutSessionCommand command) {
        return CheckoutSessionResult.builder().provider(PaymentProvider.SANDBOX)
                .providerSessionId("session:" + command.getIdempotencyKey())
                .checkoutUrl("http://localhost:3001/mock-checkout?session=" + command.getIdempotencyKey())
                .expiresAt(command.getExpiresAt()).build();
    }

    @TestConfiguration static class Beans {
        @Bean @Primary PaymentProviderProperties paymentProperties() { return new PaymentProviderProperties(); }
        @Bean PaymentMetrics paymentMetrics() { return new PaymentMetrics(new SimpleMeterRegistry()); }
    }
}
