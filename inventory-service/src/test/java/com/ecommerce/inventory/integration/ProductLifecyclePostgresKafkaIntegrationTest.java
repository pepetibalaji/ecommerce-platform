package com.ecommerce.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecommerce.common.events.product.ProductLifecycleEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.kafka.ProductLifecycleConsumer;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.service.ProductLifecycleService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = ProductLifecyclePostgresKafkaIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.config.enabled=false", "spring.profiles.active=test",
                "spring.jpa.hibernate.ddl-auto=validate", "grpc.server.port=-1",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=com.ecommerce.common.events.product",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
        })
@Testcontainers(disabledWithoutDocker = true)
class ProductLifecyclePostgresKafkaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(System.getProperty("test.postgres.image", "postgres:16-alpine")));
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse(System.getProperty("test.kafka.image", "apache/kafka:4.3.0")));

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Inventory.class)
    @EnableJpaRepositories(basePackageClasses = InventoryRepository.class)
    @Import({ProductLifecycleService.class, ProductLifecycleConsumer.class})
    static class TestApplication {}

    @Autowired InventoryRepository inventories;
    @Autowired ProductLifecycleService lifecycle;
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void kafkaSnapshotRecoversMissedCreateAndPreservesStockAcrossDuplicateReorderedAndRecoveryEvents()
            throws Exception {
        UUID productId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        ProductLifecycleEvent update = event(productId, sellerId, 3, true, "product.updated");
        publish(update);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Inventory inventory = inventories.findByProductId(productId).orElseThrow();
            assertThat(inventory.getProductVersion()).isEqualTo(3);
            assertThat(inventory.getAvailableStock()).isZero();
            assertThat(inventory.getSellerId()).isEqualTo(sellerId);
        });
        jdbc.update("update inventory set available_stock=17, reserved_stock=4 where product_id=?", productId);

        publish(update);
        publish(event(productId, sellerId, 2, false, "product.deactivated"));
        publish(event(productId, sellerId, 6, false, "product.archived"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Inventory inventory = inventories.findByProductId(productId).orElseThrow();
            assertThat(inventory.getProductVersion()).isEqualTo(6);
            assertThat(inventory.isProductActive()).isFalse();
            assertThat(inventory.getAvailableStock()).isEqualTo(17);
            assertThat(inventory.getReservedStock()).isEqualTo(4);
        });

        // The create, intermediate versions, and reactivation were missed; the latest snapshot heals it.
        ProductLifecycleEvent reconciliation = event(productId, sellerId, 9, true, "product.reconciled");
        publish(reconciliation);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Inventory inventory = inventories.findByProductId(productId).orElseThrow();
            assertThat(inventory.getProductVersion()).isEqualTo(9);
            assertThat(inventory.getLastProductEventId()).isEqualTo(reconciliation.eventId());
            assertThat(inventory.isProductActive()).isTrue();
            assertThat(inventory.getAvailableStock()).isEqualTo(17);
            assertThat(inventory.getReservedStock()).isEqualTo(4);
        });
        assertThat(lifecycle.apply(update)).isFalse();
        assertThat(lifecycle.apply(event(productId, sellerId, 9, false, "product.reconciled"))).isFalse();
        assertThat(inventories.findByProductId(productId).orElseThrow().isProductActive()).isTrue();
    }

    @Test
    void concurrentLegacyAndLifecycleProvisioningConvergesToOneRowWithoutResettingState() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        ProductLifecycleEvent event = event(productId, sellerId, 5, false, "product.archived");
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> { lifecycle.apply(event); return null; });
            tasks.add(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        inventories.insertInitialIfAbsent(UUID.randomUUID(), productId, sellerId));
                return null;
            });
        }
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (var task : workers.invokeAll(tasks)) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from inventory where product_id=?", Long.class, productId))
                .isEqualTo(1);
        Inventory inventory = inventories.findByProductId(productId).orElseThrow();
        assertThat(inventory.getProductVersion()).isEqualTo(5);
        assertThat(inventory.isProductActive()).isFalse();
        assertThat(inventory.getAvailableStock()).isZero();
    }

    private void publish(ProductLifecycleEvent event) throws Exception {
        kafka.send(KafkaTopics.PRODUCT_LIFECYCLE, event.productId().toString(), event).get(15, TimeUnit.SECONDS);
    }

    private ProductLifecycleEvent event(UUID productId, UUID sellerId, long version, boolean active, String type) {
        return new ProductLifecycleEvent(UUID.randomUUID(), productId, sellerId, Instant.now(), type, 1,
                version, active, "Test product", BigDecimal.TEN, "INR", sellerId,
                "Plain text description", "Test category", "Test brand", List.of());
    }
}
