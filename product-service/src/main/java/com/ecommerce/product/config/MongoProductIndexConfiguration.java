package com.ecommerce.product.config;

import com.ecommerce.product.entity.Product;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;

/** Ensures the catalog indexes are present independently of external YAML settings. */
@Configuration
public class MongoProductIndexConfiguration {

    @Bean
    ApplicationRunner productIndexInitializer(MongoTemplate mongoTemplate) {
        return arguments -> {
            var indexOperations = mongoTemplate.indexOps(Product.class);
            // The MongoDB _id index is created automatically and is unique.
            indexOperations.ensureIndex(new Index().on("category", Direction.ASC).named("category_idx"));
            indexOperations.ensureIndex(new Index().on("price", Direction.ASC).named("price_idx"));
            indexOperations.ensureIndex(new CompoundIndexDefinition(
                    new Document("category", 1).append("price", 1))
                    .named("category_price_idx"));
        };
    }
}
