package com.ecommerce.payment.enums;


public enum PaymentStatus {
    PENDING,
    REQUIRES_CUSTOMER_ACTION,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUND_REQUESTED,
    REFUND_PROCESSING,
    REFUNDED,
    REFUND_FAILED
}