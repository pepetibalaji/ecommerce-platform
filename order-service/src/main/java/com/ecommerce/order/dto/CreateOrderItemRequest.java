package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

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

    /**
     * Accepted during the rollout so older clients remain compatible. It is deliberately
     * ignored: checkout always obtains the current price from Product Service.
     */
    @Deprecated
    @Schema(deprecated = true, description = "Ignored. Checkout prices are calculated by Order Service.")
    private BigDecimal price;

}
