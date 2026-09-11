package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogueContractTest {
    @Mock ProductRepository productRepository;
    @Mock ProductMapper productMapper;
    @InjectMocks ProductService productService;

    @Test
    void supportsOneSidedPriceFilteringAlongsideSearchAndBrand() {
        Product product = Product.builder().id(UUID.randomUUID()).name("Phone").price(BigDecimal.TEN).build();
        when(productRepository.searchPublicProducts(eq("pho"), eq("Mobile"), eq("Acme"),
                eq(BigDecimal.TEN), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toResponse(product)).thenReturn(ProductResponse.builder().id(product.getId()).build());

        productService.getAllProducts(0, 20, "pho", "Mobile", "Acme", BigDecimal.TEN, null, "price_asc");

        verify(productRepository).searchPublicProducts(eq("pho"), eq("Mobile"), eq("Acme"),
                eq(BigDecimal.TEN), isNull(), any(Pageable.class));
    }

    @Test
    void rejectsInvalidCatalogueParameters() {
        assertThrows(IllegalArgumentException.class, () -> productService.getAllProducts(-1, 10, null, null, null, null, null, "newest"));
        assertThrows(IllegalArgumentException.class, () -> productService.getAllProducts(0, 101, null, null, null, null, null, "newest"));
        assertThrows(IllegalArgumentException.class, () -> productService.getAllProducts(0, 10, null, null, null, BigDecimal.TEN, BigDecimal.ONE, "newest"));
        assertThrows(IllegalArgumentException.class, () -> productService.getAllProducts(0, 10, null, null, null, null, null, "random"));
    }

    @Test
    void doesNotExposeInactiveProductPublicly() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.of(Product.builder().id(id).active(false).build()));

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(id));
    }
}
