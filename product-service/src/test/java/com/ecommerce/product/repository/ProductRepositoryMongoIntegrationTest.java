package com.ecommerce.product.repository;

import com.ecommerce.product.config.MongoProductIndexConfiguration;
import com.ecommerce.product.entity.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DataMongoTest(properties = "spring.cloud.config.enabled=false")
@Import(MongoProductIndexConfiguration.class)
@Testcontainers
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

    @BeforeEach
    void resetCatalogue() { productRepository.deleteAll(); }

    @Test
    void supportsEveryFilterIndependentlyAndInCombination() {
        productRepository.saveAll(List.of(product(1, "Phone", "Mobile", "Acme", "100.25"),
                product(2, "Headphones", "Audio", "Acme", "200.50"),
                product(3, "Phone case", "Mobile", "Other", "50.00")));
        var page = PageRequest.of(0, 10, Sort.by("price").and(Sort.by("id")));
        assertEquals(2, productRepository.searchPublicProducts(null, " mobile ", null, null, null, page).getTotalElements());
        assertEquals(2, productRepository.searchPublicProducts(null, null, " acme ", null, null, page).getTotalElements());
        assertEquals(2, productRepository.searchPublicProducts(null, null, null, new BigDecimal("100"), null, page).getTotalElements());
        assertEquals(2, productRepository.searchPublicProducts(null, null, null, null, new BigDecimal("101"), page).getTotalElements());
        assertEquals(List.of(id(1)), productRepository.searchPublicProducts("PHONE", "mobile", "acme",
                new BigDecimal("100.25"), new BigDecimal("100.25"), page).map(Product::getId).getContent());
        assertInstanceOf(Decimal128.class, mongoTemplate.getCollection("products")
                .find(new Document("_id", id(1).toString())).first().get("price"));
    }

    @Test
    void treatsSearchMetacharactersAsLiteralText() {
        productRepository.saveAll(List.of(product(1, "Phone .* edition", "Mobile", "Acme", "100"),
                product(2, "Other phone", "Mobile", "Acme", "100")));
        var page = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "relevanceScore").and(Sort.by("id")));
        assertEquals(List.of(id(1)), productRepository.searchPublicProducts(".*", null, null, null, null, page)
                .map(Product::getId).getContent());
        assertEquals(0, productRepository.searchPublicProducts("[abc]", null, null, null, null, page).getTotalElements());
    }

    @Test
    void relevanceRanksNameThenBrandThenDescriptionAndKeepsStablePageBoundaries() {
        Product brandExact = product(6, "A handset", "Mobile", "Phone", "100");
        Product brandContains = product(7, "A handset", "Mobile", "Telephones", "100");
        Product description = product(8, "A handset", "Mobile", "Acme", "100");
        description.setDescription("This phone is compact");
        productRepository.saveAll(List.of(product(2, "PHONE", "Mobile", null, "100"),
                product(1, "Phone", "Mobile", null, "100"),
                product(3, "Phone pro", "Mobile", null, "100"),
                product(4, "Phone ultra", "Mobile", null, "100"),
                product(5, "A phone", "Mobile", null, "100"), brandExact, brandContains, description));
        List<UUID> actual = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            var results = productRepository.searchPublicProducts(" phone ", null, null, null, null,
                    PageRequest.of(page, 2, Sort.by(Sort.Direction.DESC, "relevanceScore").and(Sort.by("id"))));
            assertEquals(8, results.getTotalElements());
            actual.addAll(results.map(Product::getId).getContent());
        }
        assertEquals(List.of(id(1), id(2), id(3), id(4), id(5), id(6), id(7), id(8)), actual);
    }

    @Test
    void hidesInactiveButPreservesNullAndMissingLegacyActiveFieldsForEveryQuery() {
        Product inactive = product(2, "Phone", "Mobile", "Acme", "100");
        inactive.setActive(false);
        Product legacy = product(3, "Phone", "Mobile", "Acme", "100");
        legacy.setActive(null);
        productRepository.saveAll(List.of(product(1, "Phone", "Mobile", "Acme", "100"), inactive, legacy,
                product(4, "Phone", "Mobile", "Acme", "100")));
        mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(id(4))), new Update().unset("active"), Product.class);
        var page = PageRequest.of(0, 10, Sort.by("id"));
        assertEquals(List.of(id(1), id(3), id(4)), productRepository.searchPublicProducts(null, null, null, null, null, page)
                .map(Product::getId).getContent());
        assertEquals(List.of(id(1), id(3), id(4)), productRepository.searchPublicProducts("phone", "mobile", "acme", null, null, page)
                .map(Product::getId).getContent());
    }

    @Test
    void caseInsensitiveFilterAndLiteralSearchUseSelectiveIndexInsteadOfScanningCatalogue() {
        List<Product> catalogue = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) catalogue.add(product(i, "Phone " + i,
                i <= 20 ? "Mobile" : "Other", "Acme", Integer.toString(i)));
        productRepository.saveAll(catalogue);
        List<String> indexes = mongoTemplate.indexOps(Product.class).getIndexInfo().stream().map(index -> index.getName()).toList();
        assertTrue(indexes.containsAll(List.of("catalogue_newest_idx", "catalogue_price_idx", "catalogue_name_idx",
                "catalogue_category_price_idx", "catalogue_brand_price_idx")));

        // No hint: prove the actual planner can choose the collation-compatible selective index.
        Document filter = new Document("active", new Document("$in", Arrays.asList(true, null)))
                .append("category", "mobile")
                .append("name", new Document("$regex", "Phone").append("$options", "i"));
        Document find = new Document("find", "products").append("filter", filter)
                .append("sort", new Document("price", 1).append("_id", 1)).append("limit", 10)
                .append("collation", new Document("locale", "en").append("strength", 2));
        Document explain = mongoTemplate.executeCommand(new Document("explain", find).append("verbosity", "executionStats"));
        String winningPlan = explain.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
        assertTrue(winningPlan.contains("IXSCAN"), winningPlan);
        assertTrue(winningPlan.contains("catalogue_category_price_idx"), winningPlan);
        Document execution = explain.get("executionStats", Document.class);
        assertEquals(10, ((Number) execution.get("nReturned")).intValue());
        assertTrue(((Number) execution.get("totalDocsExamined")).longValue() <= 20, execution.toJson());
    }

    @Test
    void facetCountsMatchCaseInsensitiveFiltersAndExcludeInactiveAndEmptyLabels() {
        Product inactive = product(4, "Archived phone", "Mobile", "Acme", "999");
        inactive.setActive(false);
        productRepository.saveAll(List.of(product(1, "Phone", "Mobile", "Acme", "100"),
                product(2, "Phone", "mobile", "acme", "200"), product(3, "Phone", "MOBILE", "ACME", "300"),
                inactive, product(5, "Uncategorized", "", null, "50")));
        var service = new com.ecommerce.product.service.ProductService(productRepository,
                new com.ecommerce.product.mapper.ProductMapper(),
                org.mockito.Mockito.mock(com.ecommerce.product.outbox.ProductOutboxService.class),
                org.mockito.Mockito.mock(com.ecommerce.product.service.SellerEligibilityClient.class), mongoTemplate);
        var facets = service.getPublicFacets();
        assertEquals(1, facets.categories().size());
        assertEquals(1, facets.brands().size());
        var category = facets.categories().getFirst();
        var brand = facets.brands().getFirst();
        assertEquals(3, category.count());
        assertEquals(3, brand.count());
        assertEquals(category.count(), productRepository.searchPublicProducts(null, category.name(), null,
                null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(brand.count(), productRepository.searchPublicProducts(null, null, brand.name(),
                null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(0, new BigDecimal("300").compareTo(facets.priceRange().max()));
    }

    private Product product(int number, String name, String category, String brand, String price) {
        return Product.builder().id(id(number)).sellerId(id(10001)).name(name).category(category).brand(brand)
                .price(new BigDecimal(price)).currency("USD").active(true).imageUrls(List.of())
                .createdAt(Instant.parse("2026-09-10T00:00:00Z")).updatedAt(Instant.parse("2026-09-10T00:00:00Z")).build();
    }

    private UUID id(int number) { return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", number)); }

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
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
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
