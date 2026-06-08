package com.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCartItemRequest {

    @NotBlank
    private String productId;

    @Min(1)
    private Integer quantity;
}