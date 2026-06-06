package com.ecommerce.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InventoryResponse {

    private UUID productId;

    private Integer availableStock;

    private Integer reservedStock;
}