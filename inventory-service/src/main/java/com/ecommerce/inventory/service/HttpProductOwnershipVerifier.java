package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
class HttpProductOwnershipVerifier implements ProductOwnershipVerifier {
    private final RestClient restClient;

    HttpProductOwnershipVerifier(@Value("${product-service.base-url:http://localhost:8082}") String productBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(productBaseUrl).build();
    }

    @Override
    public void assertOwnedBy(UUID productId, UUID sellerId) {
        ProductOwner product = restClient.get().uri("/api/v1/products/{productId}", productId)
                .retrieve().body(ProductOwner.class);
        if (product == null || !sellerId.equals(product.sellerId())) {
            throw new ResourceNotFoundException("Product not found");
        }
    }

    private record ProductOwner(UUID sellerId) { }
}
