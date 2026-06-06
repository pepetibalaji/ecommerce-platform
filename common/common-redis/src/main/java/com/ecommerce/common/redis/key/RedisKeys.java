package com.ecommerce.common.redis.key;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String inventoryLock(String productId) {
        return "inventory-lock:" + productId;
    }

    public static String orderLock(String orderId) {
        return "order-lock:" + orderId;
    }

    public static String paymentLock(String paymentId) {
        return "payment-lock:" + paymentId;
    }
}