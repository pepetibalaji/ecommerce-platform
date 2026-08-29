package com.ecommerce.product.config;

import com.ecommerce.product.entity.Product;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.math.BigDecimal;

/** Ensures the catalog indexes are present independently of external YAML settings. */
@Configuration
public class MongoProductIndexConfiguration {

    /**
     * Keeps stored price values and repository range-query parameters in the
     * same BSON Decimal128 representation.
     */
    @Bean
    MongoCustomConversions productMongoCustomConversions() {
        return MongoCustomConversions.create(adapter -> {
            adapter.registerConverter(BigDecimalToDecimal128Converter.INSTANCE);
            adapter.registerConverter(Decimal128ToBigDecimalConverter.INSTANCE);
        });
    }

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

    @WritingConverter
    enum BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {
        INSTANCE;

        @Override
        public Decimal128 convert(BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @ReadingConverter
    enum Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {
        INSTANCE;

        @Override
        public BigDecimal convert(Decimal128 source) {
            return source.bigDecimalValue();
        }
    }
}
