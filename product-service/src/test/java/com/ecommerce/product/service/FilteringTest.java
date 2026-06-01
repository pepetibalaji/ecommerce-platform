package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FilteringTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private Product product;

    private ProductResponse response;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        product = Product.builder()
                .id(UUID.randomUUID())
                .name("iPhone 15")
                .description("Apple Phone")
                .price(BigDecimal.valueOf(999))
                .category("Mobile")
                .brand("Apple")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        response = ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .brand(product.getBrand())
                .build();
    }

    @Test
    void shouldFilterByCategory() {

        Page<Product> page =
                new PageImpl<>(List.of(product));

        when(productRepository.findByCategory(
                eq("Mobile"),
                any(Pageable.class)
        )).thenReturn(page);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        productService.getAllProducts(
                0,
                10,
                "Mobile",
                null,
                null
        );

        verify(productRepository)
                .findByCategory(
                        eq("Mobile"),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldFilterByPriceRange() {

        Page<Product> page =
                new PageImpl<>(List.of(product));

        when(productRepository.findByPriceBetween(
                eq(BigDecimal.valueOf(500)),
                eq(BigDecimal.valueOf(1000)),
                any(Pageable.class)
        )).thenReturn(page);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        productService.getAllProducts(
                0,
                10,
                null,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1000)
        );

        verify(productRepository)
                .findByPriceBetween(
                        eq(BigDecimal.valueOf(500)),
                        eq(BigDecimal.valueOf(1000)),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldFilterByCategoryAndPriceRange() {

        Page<Product> page =
                new PageImpl<>(List.of(product));

        when(productRepository.findByCategoryAndPriceBetween(
                eq("Mobile"),
                eq(BigDecimal.valueOf(500)),
                eq(BigDecimal.valueOf(1000)),
                any(Pageable.class)
        )).thenReturn(page);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        productService.getAllProducts(
                0,
                10,
                "Mobile",
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1000)
        );

        verify(productRepository)
                .findByCategoryAndPriceBetween(
                        eq("Mobile"),
                        eq(BigDecimal.valueOf(500)),
                        eq(BigDecimal.valueOf(1000)),
                        any(Pageable.class)
                );
    }

    @Test
    void shouldReturnAllProductsWhenNoFiltersProvided() {

        Page<Product> page =
                new PageImpl<>(List.of(product));

        when(productRepository.findAll(
                any(Pageable.class)
        )).thenReturn(page);

        when(productMapper.toResponse(product))
                .thenReturn(response);

        productService.getAllProducts(
                0,
                10,
                null,
                null,
                null
        );

        verify(productRepository)
                .findAll(any(Pageable.class));
    }
}