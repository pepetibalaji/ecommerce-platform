package com.ecommerce.order.entity;

public enum OrderStatus {

    PENDING,

    CANCELLATION_REQUESTED,

    CONFIRMED,

    /** A paid-order cancellation has been accepted and a refund command is awaiting Payment Service. */
    REFUND_REQUESTED,

    PARTIALLY_REFUNDED,

    REFUNDED,

    /** Provider refund failed or needs reconciliation; inventory has not been released. */
    REFUND_FAILED,

    REFUND_REQUIRES_FULFILMENT_REVIEW,

    PAYMENT_FAILED,

    PAYMENT_EXPIRED,

    CANCELLED
}
