package com.ecommerce.inventory.mapper;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.entity.Inventory;

import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponse toResponse(
            Inventory inventory
    ) {

        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .availableStock(
                        inventory.getAvailableStock()
                )
                .reservedStock(
                        inventory.getReservedStock()
                )
                .build();
    }
}