package com.ecommerce.order.dto;

/** Counts are deliberately retained by state so operations can identify unpublished and terminal work. */
public record OutboxStatusCounts(long pending, long published, long completed, long failed, long manualReview) {}
