package com.ecommerce.order.catalog;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.math.BigDecimal;

import java.util.UUID;

@Component
public class ProductSellerClient {
    private final RestClient restClient;

    public ProductSellerClient(
            @Value("${product-service.base-url:http://localhost:8082}") String productBaseUrl,
            @Value("${product-service.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${product-service.read-timeout-ms:2000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().baseUrl(productBaseUrl).requestFactory(requestFactory).build();
    }

    /** Fetches the only catalog representation accepted for a purchase. */
    public OrderableProduct getOrderableProduct(UUID productId) {
        ProductDetails product;
        try {
            product = restClient.get().uri("/api/v1/products/{productId}", productId)
                    .retrieve().body(ProductDetails.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Product not found: " + productId);
            }
            throw new BadRequestException("Product catalog is temporarily unavailable; please retry your order");
        } catch (RestClientException exception) {
            // Deliberately fail closed: no order is created or stock reserved while catalog is unavailable.
            throw new BadRequestException("Product catalog is temporarily unavailable; please retry your order");
        }

        if (product == null || product.sellerId() == null || product.price() == null
                || product.name() == null || product.name().isBlank() || !product.active()) {
            throw new BadRequestException("Product is unavailable: " + productId);
        }
        return new OrderableProduct(product.id(), product.sellerId(), product.name(), product.price());
    }

    /** Kept as a compatibility helper for ownership-only callers. */
    public UUID getSellerId(UUID productId) {
        return getOrderableProduct(productId).sellerId();
    }

    private record ProductDetails(UUID id, UUID sellerId, String name, BigDecimal price, boolean active) { }
    public record OrderableProduct(UUID productId, UUID sellerId, String name, BigDecimal price) { }
}
