package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceNotFoundException;

import com.ecommerce.inventory.dto.InventoryResponse;

import com.ecommerce.inventory.entity.Inventory;

import com.ecommerce.inventory.dto.CreateInventoryRequest;

import com.ecommerce.inventory.dto.UpdateInventoryRequest;

import com.ecommerce.inventory.dto.CreateInventoryRequest;

import com.ecommerce.inventory.dto.UpdateInventoryRequest;

import com.ecommerce.inventory.dto.InventoryResponse;

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

        Inventory inventory = inventoryRepository
                .findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found"));

        if (inventory.getReservedStock() < quantity) {

            throw new IllegalArgumentException(
                    "Reserved stock is insufficient");
        }

        inventory.setReservedStock(
                inventory.getReservedStock()
                        - quantity);

        inventory.setAvailableStock(
                inventory.getAvailableStock()
                        + quantity);

        inventory.setUpdatedAt(
                LocalDateTime.now());

        Inventory savedInventory = inventoryRepository.save(
                inventory);

        return inventoryMapper.toResponse(
                savedInventory);
    }
    
    @Transactional
    public InventoryResponse deductStock(
            UUID productId,
            Integer quantity
    ) {

        Inventory inventory = inventoryRepository
                .findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found"));

        if (inventory.getReservedStock() < quantity) {

            throw new IllegalArgumentException(
                    "Reserved stock is insufficient");
        }

        inventory.setReservedStock(
                inventory.getReservedStock()
                        - quantity);

        inventory.setUpdatedAt(
                LocalDateTime.now());

        Inventory savedInventory = inventoryRepository.save(
                inventory);

        return inventoryMapper.toResponse(
                savedInventory);
    }
    
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(
            UUID productId
    ) {

        Inventory inventory = inventoryRepository
                .findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found"));

        return inventoryMapper.toResponse(
                inventory);
    }
    
    @Transactional
    public InventoryResponse createInventory(
            CreateInventoryRequest request
    ) {

        Inventory inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .productId(
                        request.getProductId())
                .availableStock(
                        request.getAvailableStock())
                .reservedStock(0)
                .updatedAt(
                        LocalDateTime.now())
                .build();

        Inventory saved = inventoryRepository.save(
                inventory);

        return inventoryMapper.toResponse(
                saved);
    }
    
    @Transactional
    public InventoryResponse updateInventory(
            UUID productId,
            UpdateInventoryRequest request
    ) {

        Inventory inventory =
                inventoryRepository
                        .findByProductId(productId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Inventory not found"
                                )
                        );

        inventory.setAvailableStock(
                request.getAvailableStock()
        );

        inventory.setUpdatedAt(
                LocalDateTime.now()
        );

        Inventory updated =
                inventoryRepository.save(
                        inventory
                );

        return inventoryMapper.toResponse(
                updated
        );
    }
}