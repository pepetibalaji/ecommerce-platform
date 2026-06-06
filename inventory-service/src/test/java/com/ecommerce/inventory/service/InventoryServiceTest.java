package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryResponse;

import com.ecommerce.inventory.entity.Inventory;

import com.ecommerce.inventory.mapper.InventoryMapper;

import com.ecommerce.inventory.repository.InventoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;

    private UUID productId;

    @BeforeEach
    void setUp() {

        productId = UUID.randomUUID();

        inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .availableStock(100)
                .reservedStock(0)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldGetInventory() {

        InventoryResponse response = InventoryResponse.builder()
                .productId(productId)
                .availableStock(100)
                .reservedStock(0)
                .build();

        when(
                inventoryRepository.findByProductId(productId)).thenReturn(
                        Optional.of(inventory));

        when(
                inventoryMapper.toResponse(inventory)).thenReturn(
                        response);

        InventoryResponse result = inventoryService.getInventory(productId);

        assertNotNull(result);

        assertEquals(
                productId,
                result.getProductId());
    }
    
    @Test
    void shouldReserveStock() {

        when(
                inventoryRepository.findByProductId(productId)).thenReturn(
                        Optional.of(inventory));

        when(
                inventoryRepository.save(any())).thenAnswer(
                        invocation -> invocation.getArgument(0));

        when(
                inventoryMapper.toResponse(any())).thenReturn(
                        InventoryResponse.builder()
                                .productId(productId)
                                .availableStock(95)
                                .reservedStock(5)
                                .build());

        InventoryResponse result = inventoryService.reserveStock(
                productId,
                5);

        assertEquals(
                95,
                result.getAvailableStock());

        assertEquals(
                5,
                result.getReservedStock());
    }
    
    @Test
    void shouldReleaseStock() {

        inventory.setAvailableStock(95);
        inventory.setReservedStock(5);

        when(
                inventoryRepository.findByProductId(productId)).thenReturn(
                        Optional.of(inventory));

        when(
                inventoryRepository.save(any())).thenAnswer(
                        invocation -> invocation.getArgument(0));

        when(
                inventoryMapper.toResponse(any())).thenReturn(
                        InventoryResponse.builder()
                                .productId(productId)
                                .availableStock(100)
                                .reservedStock(0)
                                .build());

        InventoryResponse result = inventoryService.releaseStock(
                productId,
                5);

        assertEquals(
                100,
                result.getAvailableStock());

        assertEquals(
                0,
                result.getReservedStock());
    }
    
    @Test
    void shouldDeductStock() {

        inventory.setAvailableStock(95);
        inventory.setReservedStock(5);

        when(
                inventoryRepository.findByProductId(productId)
        ).thenReturn(
                Optional.of(inventory)
        );

        when(
                inventoryRepository.save(any())
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        when(
                inventoryMapper.toResponse(any())
        ).thenReturn(
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(95)
                        .reservedStock(0)
                        .build()
        );

        InventoryResponse result =
                inventoryService.deductStock(
                        productId,
                        5
                );

        assertEquals(
                95,
                result.getAvailableStock()
        );

        assertEquals(
                0,
                result.getReservedStock()
        );
    }
}