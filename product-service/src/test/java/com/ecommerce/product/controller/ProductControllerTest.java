package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void shouldCreateProduct() throws Exception {
        ProductResponse response = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("iPhone 15")
                .description("Apple Phone")
                .price(BigDecimal.valueOf(999))
                .category("Mobile")
                .brand("Apple")
                .build();

        when(productService.createProduct(any(CreateProductRequest.class)))
                .thenReturn(response);

        CreateProductRequest request = new CreateProductRequest(
                "iPhone 15",
                "Apple Phone",
                BigDecimal.valueOf(999),
                "Mobile",
                "Apple"
        );

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldGetProductById() throws Exception {
        UUID productId = UUID.randomUUID();

        ProductResponse response = ProductResponse.builder()
                .id(productId)
                .name("iPhone 15")
                .description("Apple Phone")
                .price(BigDecimal.valueOf(999))
                .category("Mobile")
                .brand("Apple")
                .build();

        when(productService.getProductById(productId))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/products/{productId}", productId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetAllProducts() throws Exception {
        ProductResponse response = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("iPhone 15")
                .description("Apple Phone")
                .price(BigDecimal.valueOf(999))
                .category("Mobile")
                .brand("Apple")
                .build();

        Page<ProductResponse> page = new PageImpl<>(List.of(response));

        when(productService.getAllProducts(anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        UUID productId = UUID.randomUUID();

        doNothing().when(productService).deleteProduct(productId);

        mockMvc.perform(delete("/api/v1/admin/products/{productId}", productId))
                .andExpect(status().isNoContent());
    }
}