package com.ecommerce.order.catalog;

import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ProductSellerClient {
    private final RestClient restClient;

    public ProductSellerClient(@Value("${product-service.base-url:http://localhost:8082}") String productBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(productBaseUrl).build();
    }

    public UUID getSellerId(UUID productId) {
        ProductOwner product = restClient.get().uri("/api/v1/products/{productId}", productId)
                .retrieve().body(ProductOwner.class);
        if (product == null || product.sellerId() == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product.sellerId();
    }

    private record ProductOwner(UUID sellerId) { }
}
