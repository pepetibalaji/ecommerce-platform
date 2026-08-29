package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;

import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.math.BigDecimal;

import java.util.UUID;

public interface ProductRepository
        extends MongoRepository<Product, UUID> {

    Page<Product> findByCategory(
            String category,
            Pageable pageable
    );

    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);

    Page<Product> findByPriceBetween(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );

    Page<Product> findByCategoryAndPriceBetween(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );
}
