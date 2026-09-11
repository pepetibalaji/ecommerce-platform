package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.time.Duration;
import org.bson.Document;

@Repository
@RequiredArgsConstructor
class ProductRepositoryImpl implements ProductRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    @Value("${product.catalogue.query-timeout-ms:2000}")
    private long queryTimeoutMs = 2000;

    // Equality filters and name sorting share the same case-insensitive index collation.
    static final Collation CATALOGUE_COLLATION = Collation.of("en").strength(Collation.ComparisonLevel.secondary());

    @Override
    public Page<Product> searchPublicProducts(String q, String category, String brand,
                                               BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Criteria filter = publicFilter(q, category, brand, minPrice, maxPrice);
        Query query = new Query(filter).collation(CATALOGUE_COLLATION).maxTime(Duration.ofMillis(queryTimeoutMs));
        long total = mongoTemplate.count(query, Product.class);
        List<Product> products;
        if (hasText(q) && pageable.getSort().getOrderFor("relevanceScore") != null) {
            String literal = Pattern.quote(q.trim());
            Document ranking = new Document("$switch", new Document("branches", List.of(
                    rankedMatch("name", "^" + literal + "$", 600),
                    rankedMatch("name", "^" + literal, 500),
                    rankedMatch("name", literal, 400),
                    rankedMatch("brand", "^" + literal + "$", 300),
                    rankedMatch("brand", literal, 200),
                    rankedMatch("description", literal, 100))).append("default", 0));
            var aggregation = Aggregation.newAggregation(Product.class,
                    Aggregation.match(filter),
                    context -> new Document("$set", new Document("relevanceScore", ranking)),
                    context -> new Document("$sort", new Document("relevanceScore", -1).append("_id", 1)),
                    Aggregation.skip(pageable.getOffset()),
                    Aggregation.limit(pageable.getPageSize()),
                    context -> new Document("$unset", "relevanceScore"))
                    .withOptions(AggregationOptions.builder().collation(CATALOGUE_COLLATION)
                            .maxTime(Duration.ofMillis(queryTimeoutMs)).build());
            products = mongoTemplate.aggregate(aggregation, Product.class).getMappedResults();
        } else {
            products = mongoTemplate.find(query.with(pageable), Product.class);
        }
        return new PageImpl<>(products, pageable, total);
    }

    static Criteria publicFilter(String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice) {
        List<Criteria> criteria = new ArrayList<>();
        // null also matches missing fields, preserving visibility of pre-active-flag catalogue records.
        criteria.add(Criteria.where("active").in(Arrays.asList(true, null)));
        if (hasText(category)) criteria.add(Criteria.where("category").is(category.trim()));
        if (hasText(brand)) criteria.add(Criteria.where("brand").is(brand.trim()));
        if (minPrice != null || maxPrice != null) {
            Criteria price = Criteria.where("price");
            if (minPrice != null) price = price.gte(minPrice);
            if (maxPrice != null) price = price.lte(maxPrice);
            criteria.add(price);
        }
        if (hasText(q)) {
            Pattern search = Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE);
            criteria.add(new Criteria().orOperator(Criteria.where("name").regex(search),
                    Criteria.where("brand").regex(search), Criteria.where("description").regex(search)));
        }
        return new Criteria().andOperator(criteria.toArray(Criteria[]::new));
    }

    private static Document rankedMatch(String field, String regex, int score) {
        Document regexMatch = new Document("$regexMatch", new Document("input",
                new Document("$ifNull", List.of("$" + field, "")))
                .append("regex", regex).append("options", "i"));
        return new Document("case", regexMatch).append("then", score);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
