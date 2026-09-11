package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.CatalogueFacetsResponse;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.outbox.ProductOutboxService;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {
    @Value("${product.images.allowed-hosts:cdn.example.com}") private String allowedImageHosts = "cdn.example.com";
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductOutboxService outbox;
    private final SellerEligibilityClient sellerEligibilityClient;
    private final MongoTemplate mongoTemplate;
    private static final Set<String> SORTS = Set.of("relevance", "newest", "price_asc", "price_desc", "name_asc", "name_desc");

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID sellerId) {
        requireSeller(sellerId);
        return create(request, sellerId);
    }

    @Transactional
    public ProductResponse createSellerProduct(CreateProductRequest request, UUID sellerId) {
        requireSeller(sellerId);
        return create(request, sellerId);
    }

    @Transactional
    public List<ProductResponse> createProducts(List<CreateProductRequest> requests, UUID sellerId) {
        requireSeller(sellerId);
        if (requests == null || requests.isEmpty() || requests.size() > 100 || requests.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Bulk creation requires 1 to 100 non-null products");
        requests.forEach(request -> validateContent(request.getImageUrls()));
        return requests.stream().map(request -> create(request, sellerId)).toList();
    }

    private ProductResponse create(CreateProductRequest request, UUID sellerId) {
        validateContent(request.getImageUrls());
        Instant now = Instant.now();
        Product product = Product.builder().id(UUID.randomUUID()).sellerId(sellerId)
                .name(label(request.getName())).description(request.getDescription())
                .price(request.getPrice()).currency(request.getCurrency())
                .category(label(request.getCategory())).brand(label(request.getBrand()))
                .imageUrls(images(request.getImageUrls())).createdAt(now).updatedAt(now).build();
        Product saved = productRepository.save(product);
        outbox.enqueue(saved, "product.created");
        return productMapper.toResponse(saved);
    }

    private void requireSeller(UUID sellerId) {
        if (sellerId == null) throw new IllegalArgumentException("sellerId is required");
        sellerEligibilityClient.requireEligible(sellerId);
    }

    public ProductResponse getProductById(UUID productId) {
        Product product = find(productId);
        if (!product.isActive()) throw new ResourceNotFoundException("Product not found");
        return productMapper.toResponse(product);
    }

    public ProductResponse getManagedProduct(UUID productId, UUID sellerId, boolean admin) {
        return productMapper.toResponse(owned(productId, sellerId, admin));
    }

    public Page<ProductResponse> getAllProducts(int page, int size, String category, BigDecimal min, BigDecimal max) {
        return getAllProducts(page, size, null, category, null, min, max, "newest");
    }

    public Page<ProductResponse> getAllProducts(int page, int size, String q, String category, String brand,
            BigDecimal min, BigDecimal max, String sort) {
        validateQuery(page, size, q, category, brand, min, max, sort);
        return productRepository.searchPublicProducts(q, category, brand, min, max,
                PageRequest.of(page, size, toSort(sort, q))).map(productMapper::toResponse);
    }

    public CatalogueFacetsResponse getPublicFacets() {
        var active = Aggregation.match(Criteria.where("active").ne(false));
        var range = mongoTemplate.aggregate(Aggregation.newAggregation(active,
                Aggregation.group().min("price").as("min").max("price").as("max")), "products", Document.class);
        Document values = range.getUniqueMappedResult();
        return new CatalogueFacetsResponse(groupedFacets(active, "category"), groupedFacets(active, "brand"),
                new CatalogueFacetsResponse.PriceRange(values == null ? null : decimal(values.get("min")),
                        values == null ? null : decimal(values.get("max"))));
    }

    private List<CatalogueFacetsResponse.Facet> groupedFacets(org.springframework.data.mongodb.core.aggregation.MatchOperation active, String field) {
        return mongoTemplate.aggregate(Aggregation.newAggregation(active,
                Aggregation.match(Criteria.where(field).nin(null, "")), Aggregation.sort(Sort.by("_id")),
                Aggregation.group(field).count().as("count"),
                Aggregation.project("count").and("_id").as("name"), Aggregation.sort(Sort.by("name")))
                .withOptions(org.springframework.data.mongodb.core.aggregation.AggregationOptions.builder()
                        .collation(org.springframework.data.mongodb.core.query.Collation.of("en")
                                .strength(org.springframework.data.mongodb.core.query.Collation.ComparisonLevel.secondary())).build()),
                "products", Document.class).getMappedResults().stream()
                .map(d -> new CatalogueFacetsResponse.Facet(d.getString("name"), ((Number) d.get("count")).longValue())).toList();
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Decimal128 number) return number.bigDecimalValue();
        if (value instanceof BigDecimal number) return number;
        return value instanceof Number number ? new BigDecimal(number.toString()) : null;
    }

    private void validateQuery(int page, int size, String q, String category, String brand,
            BigDecimal min, BigDecimal max, String sort) {
        if (page < 0) throw new IllegalArgumentException("page must be greater than or equal to 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (q != null && q.length() > 200) throw new IllegalArgumentException("q must not exceed 200 characters");
        if (category != null && category.length() > 100 || brand != null && brand.length() > 100)
            throw new IllegalArgumentException("category and brand must not exceed 100 characters");
        if (min != null && min.signum() < 0 || max != null && max.signum() < 0)
            throw new IllegalArgumentException("Price bounds must be nonnegative");
        try {
            if (min != null) new Decimal128(min);
            if (max != null) new Decimal128(max);
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Price bounds exceed the supported decimal precision or range");
        }
        if (min != null && max != null && min.compareTo(max) > 0)
            throw new IllegalArgumentException("minPrice must not be greater than maxPrice");
        if (sort == null || !SORTS.contains(sort)) throw new IllegalArgumentException("Unsupported sort");
    }

    private Sort toSort(String sort, String q) {
        return switch (sort) {
            case "price_asc" -> Sort.by("price").ascending().and(Sort.by("id"));
            case "price_desc" -> Sort.by("price").descending().and(Sort.by("id"));
            case "name_asc" -> Sort.by("name").ascending().and(Sort.by("id"));
            case "name_desc" -> Sort.by("name").descending().and(Sort.by("id"));
            case "relevance" -> q != null && !q.isBlank()
                    ? Sort.by(Sort.Direction.DESC, "relevanceScore").and(Sort.by("id"))
                    : Sort.by("createdAt").descending().and(Sort.by("id"));
            default -> Sort.by("createdAt").descending().and(Sort.by("id"));
        };
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        return update(find(id), request);
    }

    @Transactional
    public ProductResponse updateSellerProduct(UUID id, UpdateProductRequest request, UUID sellerId, boolean admin) {
        return update(owned(id, sellerId, admin), request);
    }

    private ProductResponse update(Product product, UpdateProductRequest request) {
        boolean wasActive = product.isActive();
        if (Boolean.TRUE.equals(request.getActive()) && !wasActive) requireSeller(product.getSellerId());
        validateContent(request.getImageUrls());
        product.setName(label(request.getName())); product.setDescription(request.getDescription());
        product.setPrice(request.getPrice()); product.setCurrency(request.getCurrency());
        product.setCategory(label(request.getCategory())); product.setBrand(label(request.getBrand()));
        if (request.getActive() != null) product.setActive(request.getActive());
        product.setImageUrls(images(request.getImageUrls())); product.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(product);
        String type = wasActive && !saved.isActive() ? "product.deactivated"
                : !wasActive && saved.isActive() ? "product.reactivated" : "product.updated";
        outbox.enqueue(saved, type);
        return productMapper.toResponse(saved);
    }

    @Transactional
    public void deleteProduct(UUID id) { archive(find(id)); }

    @Transactional
    public void deleteSellerProduct(UUID id, UUID sellerId, boolean admin) { archive(owned(id, sellerId, admin)); }

    private void archive(Product product) {
        product.setActive(false); product.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(product);
        outbox.enqueue(saved, "product.archived");
    }

    @Transactional
    public ProductResponse deactivateProduct(UUID id) { return active(find(id), false); }

    @Transactional
    public ProductResponse reactivateProduct(UUID id) { return active(find(id), true); }

    @Transactional
    public ProductResponse setSellerProductActive(UUID id, UUID sellerId, boolean admin, boolean active) {
        return active(owned(id, sellerId, admin), active);
    }

    private ProductResponse active(Product product, boolean active) {
        if (product.isActive() == active) return productMapper.toResponse(product);
        if (active) requireSeller(product.getSellerId());
        product.setActive(active); product.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(product);
        outbox.enqueue(saved, active ? "product.reactivated" : "product.deactivated");
        return productMapper.toResponse(saved);
    }

    public Page<ProductResponse> getSellerProducts(UUID sellerId, int page, int size) {
        validateQuery(page, size, null, null, null, null, null, "newest");
        return productRepository.findBySellerId(sellerId, PageRequest.of(page, size, toSort("newest", null))).map(productMapper::toResponse);
    }

    private Product owned(UUID id, UUID sellerId, boolean admin) {
        Product product = find(id);
        if (!admin && !java.util.Objects.equals(sellerId, product.getSellerId()))
            throw new ResourceNotFoundException("Product not found");
        return product;
    }

    private Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private List<String> images(List<String> urls) { return urls == null ? List.of() : List.copyOf(urls); }

    private String label(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void validateContent(List<String> urls) {
        if (urls == null) return;
        Set<String> hosts = Arrays.stream((allowedImageHosts == null ? "" : allowedImageHosts).split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        for (String value : urls) {
            try {
                URI uri = URI.create(value);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getRawUserInfo() != null || uri.getFragment() != null
                        || (uri.getPort() != -1 && uri.getPort() != 443)
                        || !hosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Image URLs must use an approved HTTPS image host without credentials or fragments");
            }
        }
    }
}
