package com.ecommerce.product.service;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.outbox.ProductOutboxService;
import com.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductLabelNormalizationTest {
    @Mock ProductRepository repository;
    @Mock ProductOutboxService outbox;
    @Mock SellerEligibilityClient sellers;
    @Mock MongoTemplate mongo;
    ProductService service;

    @BeforeEach
    void createService() { service = new ProductService(repository, new ProductMapper(), outbox, sellers, mongo); }

    @Test
    void newlyCreatedLabelsMatchTheirTrimmedFacetQueries() {
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.createSellerProduct(CreateProductRequest.builder().name("  Phone  ")
                .category("  Mobile  ").brand("  ").price(BigDecimal.TEN).build(), UUID.randomUUID());
        assertEquals("Phone", response.getName());
        assertEquals("Mobile", response.getCategory());
        assertNull(response.getBrand());
    }

    @Test
    void updatingLabelsTrimsTextAndClearsBlankFacets() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(Product.builder().id(id).active(true).build()));
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.updateProduct(id, UpdateProductRequest.builder().name(" Phone Pro ")
                .category("  ").brand(" Acme ").price(BigDecimal.TEN).build());
        assertEquals("Phone Pro", response.getName());
        assertNull(response.getCategory());
        assertEquals("Acme", response.getBrand());
    }

    @Test
    void nullBulkItemsCannotReachPersistence() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createProducts(Arrays.asList((CreateProductRequest) null), UUID.randomUUID()));
        verifyNoInteractions(repository, outbox);
    }
}
