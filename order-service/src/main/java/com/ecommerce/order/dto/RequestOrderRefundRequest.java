package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Admin command for a full refund of a confirmed order. */
@Data
public class RequestOrderRefundRequest {
    @NotBlank(message = "Refund reason is required")
    @Size(max = 1_000, message = "Refund reason must be at most 1000 characters")
    private String reason;
}
