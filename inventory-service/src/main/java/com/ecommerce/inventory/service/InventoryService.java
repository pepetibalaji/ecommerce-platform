package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceNotFoundException;

import com.ecommerce.inventory.dto.InventoryResponse;

import com.ecommerce.inventory.entity.Inventory;

import com.ecommerce.inventory.mapper.InventoryMapper;

import com.ecommerce.inventory.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    @Transactional
    public InventoryResponse reserveStock(
            UUID productId,
            Integer quantity
    ) {

        Inventory inventory = inventoryRepository
                .findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found"));

        if (inventory.getAvailableStock() < quantity) {

            throw new IllegalArgumentException(
                    "Insufficient stock available");
        }

        inventory.setAvailableStock(
                inventory.getAvailableStock()
                        - quantity);

        inventory.setReservedStock(
                inventory.getReservedStock()
                        + quantity);

        inventory.setUpdatedAt(
                LocalDateTime.now());

        Inventory savedInventory = inventoryRepository.save(
                inventory);

        return inventoryMapper.toResponse(
                savedInventory);
    }
    
    @Transactional
    public InventoryResponse releaseStock(
            UUID productId,
            Integer quantity
    ) {

        Inventory inventory =
                inventoryRepository
                        .findByProductId(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Inventory not found"
                                )
                        );

        if (inventory.getReservedStock() < quantity) {

            throw new IllegalArgumentException(
                    "Reserved stock is insufficient"
            );
        }

        inventory.setReservedStock(
                inventory.getReservedStock()
                        - quantity
        );

        inventory.setAvailableStock(
                inventory.getAvailableStock()
                        + quantity
        );

        inventory.setUpdatedAt(
                LocalDateTime.now()
        );

        Inventory savedInventory =
                inventoryRepository.save(
                        inventory
                );

        return inventoryMapper.toResponse(
                savedInventory
        );
    }
}