package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderItemRequest {

    @NotNull
    private UUID productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
}