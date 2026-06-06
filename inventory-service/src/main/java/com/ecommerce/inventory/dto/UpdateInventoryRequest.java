package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class UpdateInventoryRequest {

    @NotNull
    @Min(0)
    private Integer availableStock;
}