package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @RequestParam UUID sellerId
    ) {
        return productService.createProduct(request, sellerId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductResponse> createProducts(
            @Valid @RequestBody List<CreateProductRequest> requests,
            @RequestParam UUID sellerId
    ) {
        return productService.createProducts(requests, sellerId);
    }

    @GetMapping("/products/{productId}")
    public ProductResponse getProductById(
            @PathVariable("productId") UUID productId
    ) {
        return productService.getProductById(productId);
    }

    @GetMapping("/products")
    public Page<ProductResponse> getAllProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice
    ) {
        return productService.getAllProducts(page, size, category, minPrice, maxPrice);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/products/{productId}")
    public ProductResponse updateProduct(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(productId, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable("productId") UUID productId
    ) {
        productService.deleteProduct(productId);
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @PostMapping("/seller/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createSellerProduct(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProductRequest request) {
        return productService.createSellerProduct(request, currentUserId(jwt));
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @GetMapping("/seller/products")
    public Page<ProductResponse> getSellerProducts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.getSellerProducts(currentUserId(jwt), page, size);
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @PutMapping("/seller/products/{productId}")
    public ProductResponse updateSellerProduct(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateSellerProduct(productId, request, currentUserId(jwt), isAdmin(jwt));
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @DeleteMapping("/seller/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSellerProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        productService.deleteSellerProduct(productId, currentUserId(jwt), isAdmin(jwt));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }

    private boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }
}
