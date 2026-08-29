package com.ecommerce.product.entity;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;

import java.time.LocalDateTime;

import java.util.List;
import java.util.UUID;

@Document(collection = "products")
@CompoundIndex(name = "category_price_idx", def = "{'category': 1, 'price': 1}")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Product {

    // MongoDB's required _id index is unique; UUID values are stored as strings.
    @MongoId(targetType = FieldType.STRING)
    private UUID id;

    @Indexed(name = "seller_id_idx")
    private UUID sellerId;

    private String name;

    private String description;

    @Indexed(name = "price_idx")
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    @Indexed(name = "category_idx")
    private String category;

    private String brand;

    @Builder.Default
    private List<String> imageUrls = List.of();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
