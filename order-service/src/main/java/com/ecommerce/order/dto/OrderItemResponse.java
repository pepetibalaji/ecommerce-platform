package com.ecommerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItemResponse(UUID id, UUID productId, Integer quantity, BigDecimal price) {
        this(id, productId, null, quantity, price, price == null || quantity == null ? null : price.multiply(BigDecimal.valueOf(quantity)));
    }

    @JsonIgnore
    public BigDecimal getPrice() { return unitPrice; }
}
