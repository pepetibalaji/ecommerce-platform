package com.ecommerce.product.config;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.outbox.ProductOutboxEvent;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class ProductTransactionConfiguration {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    ApplicationRunner productOutboxSchema(MongoTemplate mongo) {
        return args -> {
            // Create collections before the first transaction. No business records are removed.
            mongo.indexOps(Product.class).ensureIndex(new Index().on("version", Sort.Direction.ASC));
            mongo.updateMulti(Query.query(Criteria.where("version").is(null)),
                    new Update().set("version", 0L), Product.class);
            // Schema backfill must not increment @Version without a corresponding lifecycle event.
            mongo.getCollection("products").updateMany(new org.bson.Document("currency", null),
                    new org.bson.Document("$set", new org.bson.Document("currency", "USD")));
            mongo.indexOps(ProductOutboxEvent.class).ensureIndex(new Index()
                    .on("status", Sort.Direction.ASC).on("nextAttemptAt", Sort.Direction.ASC)
                    .on("leaseUntil", Sort.Direction.ASC).on("occurredAt", Sort.Direction.ASC)
                    .named("outbox_delivery_idx"));
        };
    }
}
