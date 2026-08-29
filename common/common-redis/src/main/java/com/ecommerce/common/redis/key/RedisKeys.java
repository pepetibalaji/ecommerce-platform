package com.ecommerce.common.redis.key;

public final class RedisKeys {

    private RedisKeys() {
    }

    // Distributed Locks

    public static String inventoryLock(String productId) {
        return "inventory-lock:" + productId;
    }

    public static String orderLock(String orderId) {
        return "order-lock:" + orderId;
    }

    public static String paymentLock(String paymentId) {
        return "payment-lock:" + paymentId;
    }

    // Rate Limiting

    public static String rateLimit(String userId) {
        return "rate-limit:" + userId;
    }

    // Future Cart Service

    public static String cart(String userId) {
        return "cart:" + userId;
    }

    public static String guestCart(String guestId) {
        return "guest-cart:" + guestId;
    }

    public static String cartLock(String ownerType, String ownerId) {
        return "cart-lock:" + ownerType + ":" + ownerId;
    }

    // Future Auth Service

    public static String refreshToken(String userId) {
        return "refresh:" + userId;
    }

    public static String otp(String email) {
        return "otp:" + email;
    }

    // Future Product Cache

    public static String product(String productId) {
        return "product:" + productId;
    }
}
