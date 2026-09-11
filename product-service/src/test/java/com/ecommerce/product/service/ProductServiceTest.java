package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.UpdateProductRequest;
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
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

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
                .imageUrls(List.of("https://cdn.example.com/products/iphone-15.jpg"))
                .build();
    }

    @Test
    void shouldCreateProduct() {
        CreateProductRequest request = new CreateProductRequest(
                "iPhone 15",
                "Apple Phone",
                BigDecimal.valueOf(999),
                "Mobile",
                "Apple",
                List.of("https://cdn.example.com/products/iphone-15.jpg")
        );

        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(response);

        UUID sellerId = UUID.randomUUID();
        ProductResponse result = productService.createProduct(request, sellerId);
        verify(sellerEligibilityClient).requireEligible(sellerId);

        assertNotNull(result);
        assertEquals(product.getId(), result.getId());
        assertEquals(List.of("https://cdn.example.com/products/iphone-15.jpg"),
                result.getImageUrls());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldGetProductById() {
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        ProductResponse result = productService.getProductById(product.getId());

        assertNotNull(result);
        assertEquals(product.getId(), result.getId());
    }

    @Test
    void sellerCannotUpdateAnotherSellersProduct() {
        product.setSellerId(UUID.randomUUID());
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Changed").price(BigDecimal.TEN).build();

        assertThrows(ResourceNotFoundException.class, () -> productService.updateSellerProduct(
                product.getId(), request, UUID.randomUUID(), false));
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        UUID id = UUID.randomUUID();

        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(id));
    }

    @Test
    void shouldDeleteProduct() {
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        productService.deleteProduct(product.getId());

        verify(productRepository).save(product);
        assertFalse(product.isActive());
    }

    @Test
    void shouldReturnProductsPage() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.searchPublicProducts(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        Page<ProductResponse> result = productService.getAllProducts(0, 10, null, null, null);

        assertEquals(1, result.getTotalElements());
        assertEquals(product.getId(), result.getContent().get(0).getId());
    }
}
