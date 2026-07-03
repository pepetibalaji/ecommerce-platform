package com.ecommerce.payment.enums;

public enum PaymentAttemptStatus {
    CREATED,
    REQUIRES_CUSTOMER_ACTION,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED
}