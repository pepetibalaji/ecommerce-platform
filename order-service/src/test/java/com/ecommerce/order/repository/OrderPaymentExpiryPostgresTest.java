package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderRefundRequestOutbox;
import com.ecommerce.order.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.cloud.config.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderPaymentExpiryPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired OrderRepository orders;
    @Autowired OrderRefundRequestOutboxRepository commands;

    @Test void unresolvedOldExpiryCommandCannotStarveLaterExpiredOrders() {
        Instant now = Instant.now();
        Order oldest = order(now.minusSeconds(300));
        Order later = order(now.minusSeconds(100));
        var sent = new OrderRefundRequestOutbox(oldest.getId(), null, oldest.getUserId(), null, "ORDER_SYSTEM",
                oldest.getTotalAmount(), oldest.getCurrency(), "Payment window expired", now);
        sent.asCancellation(true);
        commands.saveAndFlush(sent);
        assertThat(orders.lockExpiredPending(now, 1)).extracting(Order::getId).containsExactly(later.getId());
        assertThat(orders.findById(oldest.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    private Order order(Instant expiresAt) {
        return orders.saveAndFlush(Order.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .totalAmount(new BigDecimal("200.00")).currency("INR").status(OrderStatus.PENDING)
                .paymentExpiresAt(expiresAt).build());
    }
}