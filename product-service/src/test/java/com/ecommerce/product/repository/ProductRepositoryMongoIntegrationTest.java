package com.ecommerce.product.repository;

import com.ecommerce.product.config.MongoProductIndexConfiguration;
import com.ecommerce.product.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest(properties = "spring.cloud.config.enabled=false")
@Import(MongoProductIndexConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ProductRepositoryMongoIntegrationTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @DynamicPropertySource
    static void configureMongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void persistsUuidAndSupportsCatalogIndexesAndFilters() {
        UUID id = UUID.randomUUID();
        Product product = Product.builder()
                .id(id)
                .name("Wireless Headphones")
                .description("Noise-cancelling headphones")
                .price(new BigDecimal("2999.00"))
                .category("Electronics")
                .brand("Acme")
                .imageUrls(List.of("https://cdn.example.com/products/" + id + "/image-1.jpg"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        productRepository.save(product);

        assertEquals(id, productRepository.findById(id).orElseThrow().getId());
        assertEquals(1, productRepository.findByCategory("Electronics", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, productRepository.findByPriceBetween(new BigDecimal("2000"), new BigDecimal("3000"), PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, productRepository.findByCategoryAndPriceBetween("Electronics", new BigDecimal("2000"), new BigDecimal("3000"), PageRequest.of(0, 10)).getTotalElements());

        var indexNames = mongoTemplate.indexOps(Product.class).getIndexInfo().stream()
                .map(index -> index.getName())
                .toList();
        assertTrue(indexNames.containsAll(List.of("_id_", "category_idx", "price_idx", "category_price_idx")));
    }
}
