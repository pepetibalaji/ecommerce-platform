package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CreateProductRequest;

import com.ecommerce.product.dto.ProductResponse;

import com.ecommerce.product.dto.UpdateProductRequest;

import com.ecommerce.product.service.ProductService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;

import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor

public class ProductController {

    private final ProductService productService;

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)

    public ProductResponse createProduct(
            @Valid
            @RequestBody
            CreateProductRequest request
    ) {

            return productService.createProduct(request);
    }
    
        @PostMapping("/admin/products/bulk")
        @ResponseStatus(HttpStatus.CREATED)

        public List<ProductResponse> createProducts(
                @Valid
                @RequestBody
                List<CreateProductRequest> requests
        ) {

        return productService.createProducts(requests);
        }

    @GetMapping("/products/{productId}")

    public ProductResponse getProductById(
            @PathVariable("productId") UUID productId
    ) {

            return productService.getProductById(productId);
    }
    
    

       @GetMapping("/products")
        public Page<ProductResponse> getAllProducts(

                @RequestParam(name = "page", defaultValue = "0")
                int page,

                @RequestParam(name = "size", defaultValue = "10")
                int size,

                @RequestParam(name = "category", required = false)
                String category,

                @RequestParam(name = "minPrice", required = false)
                BigDecimal minPrice,

                @RequestParam(name = "maxPrice", required = false)
                BigDecimal maxPrice
        ) {

        return productService.getAllProducts(
                page,
                size,
                category,
                minPrice,
                maxPrice
        );
        }

    @PutMapping("/admin/products/{productId}")

    public ProductResponse updateProduct(
            @PathVariable("productId") UUID productId,

            @Valid
            @RequestBody
            UpdateProductRequest request
    ) {

        return productService.updateProduct(
                productId,
                request
        );
    }

    @DeleteMapping("/admin/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)

    public void deleteProduct(
            @PathVariable("productId") UUID productId
    ) {

        productService.deleteProduct(productId);
    }
}