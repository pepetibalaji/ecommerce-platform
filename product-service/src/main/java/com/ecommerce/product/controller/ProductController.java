package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.CatalogueFacetsResponse;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.outbox.ProductOutboxService;
import com.ecommerce.product.outbox.ProductReconciliationService;
import com.ecommerce.product.dto.CataloguePageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductOutboxService outbox;
    private final ProductReconciliationService reconciliation;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @RequestParam UUID sellerId
    ) {
        return productService.createProduct(request, sellerId);
    }

    @GetMapping("/products/{productId}")
    public ProductResponse getProductById(
            @PathVariable("productId") UUID productId
    ) {
        return productService.getProductById(productId);
    }

    @GetMapping("/products")
    public CataloguePageResponse<ProductResponse> getAllProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "sort", defaultValue = "newest") String sort
    ) {
        return CataloguePageResponse.from(productService.getAllProducts(page, size, q, category, brand, minPrice, maxPrice, sort));
    }

    @GetMapping("/products/facets")
    public CatalogueFacetsResponse getFacets() { return productService.getPublicFacets(); }

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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products/{productId}/deactivate")
    public ProductResponse deactivateProduct(@PathVariable UUID productId) { return productService.deactivateProduct(productId); }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products/{productId}/reactivate")
    public ProductResponse reactivateProduct(@PathVariable UUID productId) { return productService.reactivateProduct(productId); }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @PostMapping("/seller/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createSellerProduct(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProductRequest request) {
        return productService.createSellerProduct(request, currentUserId(jwt));
    }

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/seller/products/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductResponse> createSellerProducts(@AuthenticationPrincipal Jwt jwt,
            @Size(min = 1, max = 100, message = "Bulk creation requires 1 to 100 products") @Valid @RequestBody List<@jakarta.validation.constraints.NotNull(message = "Product must not be null") @Valid CreateProductRequest> requests) {
        return productService.createProducts(requests, currentUserId(jwt));
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @GetMapping("/seller/products")
    public CataloguePageResponse<ProductResponse> getSellerProducts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return CataloguePageResponse.from(productService.getSellerProducts(currentUserId(jwt), page, size));
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
        try { return UUID.fromString(jwt.getClaimAsString("userId")); }
        catch (RuntimeException invalid) { throw new com.ecommerce.common.exception.UnauthorizedException("Invalid user identity"); }
    }

    private boolean isAdmin(Jwt jwt) {
        Object roles = jwt.getClaim("roles");
        if (roles instanceof Collection<?> values) {
            return values.stream().anyMatch(role -> "ADMIN".equals(String.valueOf(role).replace("ROLE_", "")));
        }
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products/outbox/replay-dead-letters")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replayDeadLetters() { outbox.replayDeadLetters(); }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products/outbox/reconcile")
    public ProductReconciliationService.Result reconcile(@RequestParam(required = false) UUID afterId,
            @RequestParam(defaultValue = "100") int size) { return reconciliation.reconcile(afterId, size); }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/products/{productId}")
    public ProductResponse getAdminProduct(@PathVariable UUID productId) {
        return productService.getManagedProduct(productId, null, true);
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @GetMapping("/seller/products/{productId}")
    public ProductResponse getSellerProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return productService.getManagedProduct(productId, currentUserId(jwt), isAdmin(jwt));
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @PostMapping("/seller/products/{productId}/deactivate")
    public ProductResponse deactivateSellerProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return productService.setSellerProductActive(productId, currentUserId(jwt), isAdmin(jwt), false);
    }

    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @PostMapping("/seller/products/{productId}/reactivate")
    public ProductResponse reactivateSellerProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return productService.setSellerProductActive(productId, currentUserId(jwt), isAdmin(jwt), true);
    }
}
