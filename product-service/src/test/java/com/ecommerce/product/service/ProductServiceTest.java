package com.ecommerce.product.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

        ProductResponse result = productService.createProduct(request);

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
    void shouldThrowWhenProductNotFound() {
        UUID id = UUID.randomUUID();

        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(id));
    }

    @Test
    void shouldDeleteProduct() {
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        productService.deleteProduct(product.getId());

        verify(productRepository).delete(product);
    }

    @Test
    void shouldReturnProductsPage() {
        Page<Product> page = new PageImpl<>(List.of(product));

        when(productRepository.findAll(any(PageRequest.class))).thenReturn(page);
        when(productMapper.toResponse(product)).thenReturn(response);

        Page<ProductResponse> result = productService.getAllProducts(0, 10, null, null, null);

        assertEquals(1, result.getTotalElements());
        assertEquals(product.getId(), result.getContent().get(0).getId());
    }
}
