package com.ecommerce.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Optional customer explanation retained in the order lifecycle audit record. */
@Data
public class CancelOrderRequest {
    @Size(max = 1_000, message = "Cancellation reason must be at most 1000 characters")
    private String reason;
}
