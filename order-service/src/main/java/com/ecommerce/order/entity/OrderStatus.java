package com.ecommerce.order.entity;

public enum OrderStatus {

    PENDING,

    CONFIRMED,

    PARTIALLY_REFUNDED,

    REFUNDED,

    REFUND_REQUIRES_FULFILMENT_REVIEW,

    PAYMENT_FAILED,

    CANCELLED
}
