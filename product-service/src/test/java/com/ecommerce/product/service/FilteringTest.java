package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.outbox.ProductOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilteringTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductOutboxService outbox;

    @Mock
    private SellerEligibilityClient sellerEligibilityClient;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private ProductResponse response;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(UUID.randomUUID())
                .name("iPhone 15")
                .description("Apple Phone")
                .price(BigDecimal.valueOf(999))
                .category("Mobile")
                .brand("Apple")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
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
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(isNull(), eq("Mobile"), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        productService.getAllProducts(0, 10, "Mobile", null, null);

        verify(productRepository).searchPublicProducts(isNull(), eq("Mobile"), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void shouldFilterByPriceRange() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(isNull(), isNull(), isNull(),
                eq(BigDecimal.valueOf(500)),
                eq(BigDecimal.valueOf(1000)),
                any(Pageable.class)
        )).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        productService.getAllProducts(
                0,
                10,
                null,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1000)
        );

        verify(productRepository).searchPublicProducts(isNull(), isNull(), isNull(),
                eq(BigDecimal.valueOf(500)),
                eq(BigDecimal.valueOf(1000)),
                any(Pageable.class)
        );
    }

    @Test
    void shouldFilterByCategoryAndPriceRange() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(isNull(),
                eq("Mobile"), isNull(),
                eq(BigDecimal.valueOf(500)),
                eq(BigDecimal.valueOf(1000)),
                any(Pageable.class)
        )).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        productService.getAllProducts(
                0,
                10,
                "Mobile",
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1000)
        );

        verify(productRepository).searchPublicProducts(isNull(),
                eq("Mobile"), isNull(),
                eq(BigDecimal.valueOf(500)),
                eq(BigDecimal.valueOf(1000)),
                any(Pageable.class)
        );
    }

    @Test
    void shouldReturnAllProductsWhenNoFiltersProvided() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        productService.getAllProducts(0, 10, null, null, null);

        verify(productRepository).searchPublicProducts(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }
}
