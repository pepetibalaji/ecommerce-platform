package com.ecommerce.product.mapper;

import com.ecommerce.product.dto.ProductResponse;

import com.ecommerce.product.entity.Product;

import org.springframework.stereotype.Component;

import java.util.List;

@Component

public class ProductMapper {

    public ProductResponse toResponse(Product product) {

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .brand(product.getBrand())
                .imageUrls(product.getImageUrls() == null ? List.of() : product.getImageUrls())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
