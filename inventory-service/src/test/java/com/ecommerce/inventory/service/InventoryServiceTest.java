package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.InventoryReservationStatus;
import com.ecommerce.inventory.mapper.InventoryMapper;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

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
        InventoryResponse response = response(100, 0);

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(inventory));
        when(inventoryMapper.toResponse(inventory)).thenReturn(response);

        InventoryResponse result = inventoryService.getInventory(productId);

        assertEquals(productId, result.getProductId());
    }

    @Test
    void shouldReserveStock() {
        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryMapper.toResponse(any())).thenReturn(response(95, 5));

        InventoryResponse result = inventoryService.reserveStock(productId, 5);

        assertEquals(95, result.getAvailableStock());
        assertEquals(5, result.getReservedStock());
        assertEquals(95, inventory.getAvailableStock());
        assertEquals(5, inventory.getReservedStock());
    }

    @Test
    void shouldReleaseStock() {
        inventory.setAvailableStock(95);
        inventory.setReservedStock(5);

        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryMapper.toResponse(any())).thenReturn(response(100, 0));

        InventoryResponse result = inventoryService.releaseStock(productId, 5);

        assertEquals(100, result.getAvailableStock());
        assertEquals(0, result.getReservedStock());
        assertEquals(100, inventory.getAvailableStock());
        assertEquals(0, inventory.getReservedStock());
    }

    @Test
    void shouldDeductStock() {
        inventory.setAvailableStock(95);
        inventory.setReservedStock(5);

        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryMapper.toResponse(any())).thenReturn(response(95, 0));

        InventoryResponse result = inventoryService.deductStock(productId, 5);

        assertEquals(95, result.getAvailableStock());
        assertEquals(0, result.getReservedStock());
        assertEquals(95, inventory.getAvailableStock());
        assertEquals(0, inventory.getReservedStock());
    }

    @Test
    void reservationAwareReserve_shouldBeIdempotent() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservation reservation = new InventoryReservation(reservationId, productId, 5);

        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(inventoryReservationRepository.findByIdForUpdate(reservationId))
                .thenReturn(Optional.empty(), Optional.of(reservation));
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryMapper.toResponse(any())).thenReturn(response(95, 5));

        inventoryService.reserveStock(productId, 5, reservationId);
        inventoryService.reserveStock(productId, 5, reservationId);

        assertEquals(95, inventory.getAvailableStock());
        assertEquals(5, inventory.getReservedStock());
        assertEquals(InventoryReservationStatus.RESERVED, reservation.getStatus());
        verify(inventoryRepository, times(1)).save(inventory);
        verify(inventoryReservationRepository, times(1)).save(any(InventoryReservation.class));
    }

    @Test
    void reservationAwareRelease_shouldBeIdempotent() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservation reservation = new InventoryReservation(reservationId, productId, 5);
        inventory.setAvailableStock(95);
        inventory.setReservedStock(5);

        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(inventoryReservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryMapper.toResponse(any())).thenReturn(response(100, 0));

        inventoryService.releaseStock(productId, 5, reservationId);
        inventoryService.releaseStock(productId, 5, reservationId);

        assertEquals(100, inventory.getAvailableStock());
        assertEquals(0, inventory.getReservedStock());
        assertEquals(InventoryReservationStatus.RELEASED, reservation.getStatus());
        verify(inventoryRepository, times(1)).save(inventory);
        verify(inventoryReservationRepository, times(1)).save(reservation);
    }

    @Test
    void reservationAwareRelease_shouldRejectReservationDetailsThatDoNotMatch() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservation reservation = new InventoryReservation(reservationId, UUID.randomUUID(), 5);
        inventory.setAvailableStock(95);
        inventory.setReservedStock(5);

        when(inventoryRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(inventory));
        when(inventoryReservationRepository.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.releaseStock(productId, 5, reservationId));

        assertEquals(95, inventory.getAvailableStock());
        assertEquals(5, inventory.getReservedStock());
        verify(inventoryRepository, never()).save(any());
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    void reservationAwareOperations_shouldRejectNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserveStock(productId, 0, UUID.randomUUID()));

        verify(inventoryRepository, never()).findByProductIdForUpdate(any());
        verify(inventoryReservationRepository, never()).findByIdForUpdate(any());
    }

    private InventoryResponse response(int availableStock, int reservedStock) {
        return InventoryResponse.builder()
                .productId(productId)
                .availableStock(availableStock)
                .reservedStock(reservedStock)
                .build();
    }
}
