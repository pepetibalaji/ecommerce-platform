package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.*;

import com.ecommerce.inventory.service.InventoryService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

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

    @PutMapping("/{productId}")
    public InventoryResponse updateInventory(
            @PathVariable UUID productId,

            @Valid
            @RequestBody
            UpdateInventoryRequest request
    ) {

        return inventoryService.updateInventory(
                productId,
                request
        );
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(
            @PathVariable UUID productId
    ) {

        return inventoryService.getInventory(
                productId
        );
    }
}