package com.ecommerce.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Checkout guardrails. Product-specific limits override the service default. */
@ConfigurationProperties(prefix = "order.checkout")
public class CheckoutProperties {

    private int maxQuantityPerProduct = 100;
    private int maxTotalQuantity = 500;
    private Map<UUID, Integer> productMaximumQuantities = new HashMap<>();

    public int getMaxQuantityPerProduct() {
        return maxQuantityPerProduct;
    }

    public void setMaxQuantityPerProduct(int maxQuantityPerProduct) {
        this.maxQuantityPerProduct = maxQuantityPerProduct;
    }

    public int getMaxTotalQuantity() {
        return maxTotalQuantity;
    }

    public void setMaxTotalQuantity(int maxTotalQuantity) {
        this.maxTotalQuantity = maxTotalQuantity;
    }

    public Map<UUID, Integer> getProductMaximumQuantities() {
        return productMaximumQuantities;
    }

    public void setProductMaximumQuantities(Map<UUID, Integer> productMaximumQuantities) {
        this.productMaximumQuantities = productMaximumQuantities == null ? new HashMap<>() : productMaximumQuantities;
    }

    public int maximumQuantityFor(UUID productId) {
        return productMaximumQuantities.getOrDefault(productId, maxQuantityPerProduct);
    }
}
