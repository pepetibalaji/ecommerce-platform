package com.ecommerce.common.events.core;

public final class EventSources {

    private EventSources() {
    }

    public static final String AUTH_SERVICE = "auth-service";
    public static final String GATEWAY_SERVICE = "gateway-service";

    public static final String PRODUCT_SERVICE = "product-service";
    public static final String INVENTORY_SERVICE = "inventory-service";
    public static final String CART_SERVICE = "cart-service";
    public static final String ORDER_SERVICE = "order-service";
    public static final String PAYMENT_SERVICE = "payment-service";
    public static final String SHIPPING_SERVICE = "shipping-service";
    public static final String NOTIFICATION_SERVICE = "notification-service";

    public static final String ADDRESS_SERVICE = "address-service";
    public static final String PRICING_SERVICE = "pricing-service";
    public static final String REVIEW_SERVICE = "review-service";
    public static final String SEARCH_SERVICE = "search-service";
    public static final String AI_SERVICE = "ai-service";

    public static final String KAFKA_ERROR_HANDLER = "kafka-error-handler";
    public static final String SYSTEM = "system";
}