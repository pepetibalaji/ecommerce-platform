package com.ecommerce.order.entity;

/** Delivery state of a durable refund request sent to the Payment Service. */
public enum RefundRequestStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
