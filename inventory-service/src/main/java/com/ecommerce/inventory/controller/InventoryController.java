package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.*;

import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.service.InventoryOutboxService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryOutboxService inventoryOutboxService;

    @PostMapping
    public InventoryResponse createInventory(
            @Valid
            @RequestBody
            CreateInventoryRequest request
    ) {

        return inventoryService.createInventory(
                request
        );
    }

    @PostMapping("/{productId}/adjustments")
    public InventoryResponse adjustInventory(
            @PathVariable UUID productId,

            @Valid
            @RequestBody
            StockAdjustmentRequest request
    ) {

        return inventoryService.adjustStock(productId, request, "admin");
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(
            @PathVariable UUID productId
    ) {

        return inventoryService.getInventory(
                productId
        );
    }

    @GetMapping("/operations")
    public InventoryOperationsResponse operations() {
        return inventoryOutboxService.operations();
    }
}
