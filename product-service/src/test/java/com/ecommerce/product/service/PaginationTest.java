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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaginationTest {

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
    void rejectsUnrepresentablePriceBoundsBeforeDatabaseAccess() {
        for (BigDecimal value : List.of(new BigDecimal("1E+6145"), new BigDecimal("1E-6177"),
                new BigDecimal("12345678901234567890123456789012345"))) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> productService.getAllProducts(0, 10, null, value, null));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> productService.getAllProducts(0, 10, null, null, value));
        }
        org.mockito.Mockito.verifyNoInteractions(productRepository);
    }

    @Test
    void shouldReturnPaginatedProducts() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        Page<ProductResponse> result = productService.getAllProducts(0, 10, null, null, null);

        assertEquals(1, result.getTotalElements());
        assertEquals(product.getId(), result.getContent().get(0).getId());
    }

    @Test
    void shouldReturnEmptyPage() {
        when(productRepository.searchPublicProducts(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        Page<ProductResponse> result = productService.getAllProducts(0, 10, null, null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseCorrectPaginationParameters() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        productService.getAllProducts(2, 5, null, null, null);

        verify(productRepository).searchPublicProducts(null, null, null, null, null, PageRequest.of(2, 5,
                Sort.by("createdAt").descending().and(Sort.by("id"))));
    }
}
