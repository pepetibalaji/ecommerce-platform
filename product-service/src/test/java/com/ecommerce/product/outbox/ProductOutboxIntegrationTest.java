package com.ecommerce.product.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecommerce.product.config.MongoProductIndexConfiguration;
import com.ecommerce.product.config.ProductTransactionConfiguration;
import com.ecommerce.product.controller.ProductController;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.SellerEligibilityClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@DataMongoTest(properties = {"spring.cloud.config.enabled=false", "product.outbox.initial-delay-ms=3600000"})
@Import({MongoProductIndexConfiguration.class, ProductTransactionConfiguration.class, ProductService.class,
        ProductMapper.class, ProductOutboxService.class, ProductReconciliationService.class, ProductOutboxIntegrationTest.Beans.class})
@Testcontainers
class ProductOutboxIntegrationTest {
    @Container static final MongoDBContainer MONGO = new MongoDBContainer(System.getProperty("test.mongo.image", "mongo:7.0"));
    @Container static final KafkaContainer KAFKA = new KafkaContainer(System.getProperty("test.kafka.image", "apache/kafka:4.3.0"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }
    @Autowired ProductService products;
    @Autowired ProductRepository repository;
    @SpyBean ProductOutboxRepository events;
    @Autowired ProductOutboxService outbox;
    @Autowired ProductReconciliationService reconciliation;
    @Autowired MongoTemplate mongo;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("productOutboxSchema")
    org.springframework.boot.ApplicationRunner upgrade;
    @SpyBean KafkaTemplate<String, Object> kafka;
    @MockBean SellerEligibilityClient sellers;
    final UUID seller = UUID.randomUUID();

    @BeforeEach void resetData() {
        reset(events, kafka);
        repository.deleteAll();
        events.deleteAll();
    }

    @Test void deliversAllLifecycleSnapshotsWithMonotonicVersionsOverKafka() throws Exception {
        UUID id = products.createSellerProduct(request("Phone"), seller).getId();
        products.updateProduct(id, UpdateProductRequest.builder().name("Phone Pro").price(BigDecimal.TEN).currency("INR").build());
        products.deactivateProduct(id);
        products.reactivateProduct(id);
        products.deleteProduct(id);
        assertThat(events.findAll()).hasSize(5);
        assertThat(repository.findById(id).orElseThrow().isActive()).isFalse();
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(ProductOutboxService.TOPIC));
            outbox.deliver();
            var received = new TreeMap<Long, String>();
            long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            ObjectMapper json = new ObjectMapper();
            while (received.size() < 5 && System.nanoTime() < end) {
                for (var record : consumer.poll(Duration.ofMillis(300))) {
                    var event = json.readTree(record.value());
                    if (id.toString().equals(event.path("productId").asText())) {
                        assertThat(record.key()).isEqualTo(id.toString());
                        assertThat(event.path("sellerId").asText()).isEqualTo(seller.toString());
                        assertThat(event.path("schemaVersion").asInt()).isEqualTo(1);
                        received.put(event.path("productVersion").asLong(), event.path("eventType").asText());
                    }
                }
            }
            assertThat(received.values()).containsExactly("product.created", "product.updated", "product.deactivated", "product.reactivated", "product.archived");
            assertThat(received.keySet()).containsExactly(1L, 2L, 3L, 4L, 5L);
        }
        assertThat(events.findAll()).allMatch(event -> event.getStatus() == ProductOutboxEvent.Status.PUBLISHED);
    }

    @Test void rollsBackProductWhenOutboxInsertFails() {
        doThrow(new IllegalStateException("simulated outbox persistence failure")).when(events).save(any(ProductOutboxEvent.class));
        assertThatThrownBy(() -> products.createProduct(request("Rollback"), seller)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test void rollsBackEntireBulkOnLaterOutboxFailure() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("second event failed");
            return mongo.save(invocation.getArgument(0, ProductOutboxEvent.class));
        }).when(events).save(any(ProductOutboxEvent.class));
        assertThatThrownBy(() -> products.createProducts(List.of(request("First"), request("Second")), seller)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test void retriesWithDelayThenDeadLettersAndReplays() {
        products.createProduct(request("Retry"), seller);
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down"))).when(kafka).send(anyString(), anyString(), any());
        outbox.deliver();
        ProductOutboxEvent event = events.findAll().getFirst();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(outbox.claim()).isNull();
        mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())),
                new Update().set("attempts", 9).set("nextAttemptAt", Instant.now().minusSeconds(1)), ProductOutboxEvent.class);
        outbox.deliver();
        assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(ProductOutboxEvent.Status.DEAD);
        reset(kafka);
        assertThat(outbox.replayDeadLetters()).isEqualTo(1);
        outbox.deliver();
        assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(ProductOutboxEvent.Status.PUBLISHED);
    }

    @Test void expiredWorkerCannotCompleteAnotherWorkersLease() {
        products.createProduct(request("Leased"), seller);
        ProductOutboxEvent first = outbox.claim();
        assertThat(outbox.claim()).isNull();
        mongo.updateFirst(Query.query(Criteria.where("id").is(first.getId())),
                new Update().set("leaseUntil", Instant.now().minusSeconds(1)), ProductOutboxEvent.class);
        ProductOutboxEvent second = outbox.claim();
        assertThat(second.getLeaseToken()).isNotEqualTo(first.getLeaseToken());
        outbox.publish(first);
        assertThat(events.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(ProductOutboxEvent.Status.PROCESSING);
        outbox.publish(second);
        assertThat(events.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(ProductOutboxEvent.Status.PUBLISHED);
    }

    @Test void reconcilesInBoundedBatchesIncludingArchivedProducts() {
        products.createProducts(List.of(request("A"), request("B"), request("C")), seller);
        products.deleteProduct(repository.findAll().getFirst().getId());
        events.deleteAll(); // Simulate a lost/expired downstream event history; products remain authoritative.
        var first = reconciliation.reconcile(null, 2);
        var last = reconciliation.reconcile(first.nextAfterId(), 2);
        assertThat(first.enqueued()).isEqualTo(2);
        assertThat(last.enqueued()).isEqualTo(1);
        assertThat(last.nextAfterId()).isNull();
        assertThat(events.findAll()).allMatch(event -> event.getType().equals("product.reconciled"));
        assertThat(events.findAll()).anyMatch(event -> !event.getPayload().active());
    }

    @Test void publicHttpCatalogueUsesRealDatabaseAndHidesArchivedProducts() throws Exception {
        UUID id = products.createProduct(CreateProductRequest.builder().name("Copper Lamp").brand("Pepe")
                .category("Home").price(new BigDecimal("1200")).currency("INR").build(), seller).getId();
        var http = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ProductController(products, outbox, reconciliation))
                .setControllerAdvice(new com.ecommerce.common.exception.GlobalExceptionHandler(),
                        new com.ecommerce.product.exception.ProductExceptionHandler()).build();
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products")
                .param("q", "lamp").param("brand", "pepe").param("category", "home")
                .param("minPrice", "1000").param("sort", "relevance").param("size", "1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].currency").value("INR"));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products").param("minPrice", "2").param("maxPrice", "1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products").param("page", "-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products/facets"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.brands[0].name").value("Pepe"));
        products.deleteProduct(id);
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products/" + id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/products"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(0));
    }

    @Test void upgradesLegacyRevisionAndRecoversOutboxRowsWithoutPayload() throws Exception {
        UUID id = products.createProduct(request("Legacy"), seller).getId();
        ProductOutboxEvent event = events.findAll().getFirst();
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), new Update().unset("version").set("currency", null), Product.class);
        mongo.updateFirst(Query.query(Criteria.where("id").is(event.getId())), new Update().unset("payload"), ProductOutboxEvent.class);
        upgrade.run(new org.springframework.boot.DefaultApplicationArguments());
        assertThat(repository.findById(id).orElseThrow().getVersion()).isEqualTo(0L);
        assertThat(repository.findById(id).orElseThrow().getCurrency()).isEqualTo("USD");
        products.updateProduct(id, UpdateProductRequest.builder().name("Legacy Updated").price(BigDecimal.TEN).currency("INR").build());
        outbox.deliver();
        var recovered = events.findById(event.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(ProductOutboxEvent.Status.PUBLISHED);
        assertThat(recovered.getPayload().eventId()).isEqualTo(event.getId());
        assertThat(recovered.getPayload().eventType()).isEqualTo("product.reconciled");
        assertThat(recovered.getPayload().productVersion()).isEqualTo(2L);
        assertThat(recovered.getPayload().name()).isEqualTo("Legacy Updated");
    }

    private CreateProductRequest request(String name) {
        return CreateProductRequest.builder().name(name).price(BigDecimal.TEN).currency("INR").build();
    }
    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "group.id", UUID.randomUUID().toString(),
                "auto.offset.reset", "earliest", "enable.auto.commit", false), new StringDeserializer(), new StringDeserializer());
    }
    @TestConfiguration static class Beans {
        @Bean MeterRegistry meters() { return new SimpleMeterRegistry(); }
        @Bean KafkaTemplate<String, Object> kafkaTemplate() {
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers(),
                    "acks", "all", "enable.idempotence", true), new StringSerializer(), new JsonSerializer<>()));
        }
    }
}
