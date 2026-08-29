package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.dto.CreateInventoryRequest;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryReservation;
import com.ecommerce.inventory.entity.InventoryReservationStatus;
import com.ecommerce.inventory.mapper.InventoryMapper;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryMapper inventoryMapper;
    private final ProductOwnershipVerifier productOwnershipVerifier;

    /**
     * Legacy, quantity-only reservation operation kept for clients that have not yet been deployed
     * with the reservation-aware gRPC contract.
     */
    @Transactional
    public InventoryResponse reserveStock(UUID productId, Integer quantity) {
        validateInventoryMutation(productId, quantity);

        Inventory inventory = getInventoryForUpdate(productId);
        ensureAvailableStock(inventory, quantity);
        reserve(inventory, quantity);

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    /**
     * Reserves stock exactly once for a stable reservation id. A retry for an already-reserved id
     * returns the current inventory without changing stock again.
     */
    @Transactional
    public InventoryResponse reserveStock(UUID productId, Integer quantity, UUID reservationId) {
        validateReservationMutation(productId, quantity, reservationId);

        Inventory inventory = getInventoryForUpdate(productId);
        InventoryReservation existingReservation = inventoryReservationRepository
                .findByIdForUpdate(reservationId)
                .orElse(null);

        if (existingReservation != null) {
            validateReservationMatches(existingReservation, productId, quantity);

            if (existingReservation.getStatus() == InventoryReservationStatus.RESERVED) {
                return inventoryMapper.toResponse(inventory);
            }

            throw new IllegalArgumentException(
                    "Inventory reservation cannot be reserved again after it was "
                            + existingReservation.getStatus().name().toLowerCase()
            );
        }

        ensureAvailableStock(inventory, quantity);
        reserve(inventory, quantity);

        Inventory savedInventory = inventoryRepository.save(inventory);
        inventoryReservationRepository.save(new InventoryReservation(reservationId, productId, quantity));

        return inventoryMapper.toResponse(savedInventory);
    }

    /**
     * Legacy, quantity-only release operation kept for rolling deployments. New callers must use
     * the reservation-aware overload so release retries are safe.
     */
    @Transactional
    public InventoryResponse releaseStock(UUID productId, Integer quantity) {
        validateInventoryMutation(productId, quantity);

        Inventory inventory = getInventoryForUpdate(productId);
        ensureReservedStock(inventory, quantity);
        release(inventory, quantity);

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    /**
     * Releases stock exactly once for a reservation. A duplicate release is a successful no-op.
     */
    @Transactional
    public InventoryResponse releaseStock(UUID productId, Integer quantity, UUID reservationId) {
        validateReservationMutation(productId, quantity, reservationId);

        Inventory inventory = getInventoryForUpdate(productId);
        InventoryReservation reservation = getReservationForUpdate(reservationId);
        validateReservationMatches(reservation, productId, quantity);

        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            return inventoryMapper.toResponse(inventory);
        }

        if (reservation.getStatus() == InventoryReservationStatus.DEDUCTED) {
            throw new IllegalArgumentException("Deducted inventory reservations cannot be released");
        }

        ensureReservedStock(inventory, quantity);
        release(inventory, quantity);
        reservation.release();

        Inventory savedInventory = inventoryRepository.save(inventory);
        inventoryReservationRepository.save(reservation);

        return inventoryMapper.toResponse(savedInventory);
    }

    /**
     * Legacy, quantity-only deduction operation kept for clients that have not yet adopted the
     * reservation ledger.
     */
    @Transactional
    public InventoryResponse deductStock(UUID productId, Integer quantity) {
        validateInventoryMutation(productId, quantity);

        Inventory inventory = getInventoryForUpdate(productId);
        ensureReservedStock(inventory, quantity);
        deduct(inventory, quantity);

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    /**
     * Deducts stock exactly once when a reservation is fulfilled. A duplicate deduction is a
     * successful no-op; a released reservation cannot later be deducted.
     */
    @Transactional
    public InventoryResponse deductStock(UUID productId, Integer quantity, UUID reservationId) {
        validateReservationMutation(productId, quantity, reservationId);

        Inventory inventory = getInventoryForUpdate(productId);
        InventoryReservation reservation = getReservationForUpdate(reservationId);
        validateReservationMatches(reservation, productId, quantity);

        if (reservation.getStatus() == InventoryReservationStatus.DEDUCTED) {
            return inventoryMapper.toResponse(inventory);
        }

        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            throw new IllegalArgumentException("Released inventory reservations cannot be deducted");
        }

        ensureReservedStock(inventory, quantity);
        deduct(inventory, quantity);
        reservation.deduct();

        Inventory savedInventory = inventoryRepository.save(inventory);
        inventoryReservationRepository.save(reservation);

        return inventoryMapper.toResponse(savedInventory);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID productId) {
        Inventory inventory = inventoryRepository
                .findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));

        return inventoryMapper.toResponse(inventory);
    }

    @Transactional
    public InventoryResponse createInventory(CreateInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new ResourceAlreadyExistsException(
                    "Inventory already exists for product: " + request.getProductId()
            );
        }

        Inventory inventory = Inventory.builder()
                .id(UUID.randomUUID())
                .productId(request.getProductId())
                .availableStock(request.getAvailableStock())
                .reservedStock(0)
                .updatedAt(LocalDateTime.now())
                .build();

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Transactional
    public InventoryResponse updateInventory(UUID productId, UpdateInventoryRequest request) {
        Inventory inventory = getInventoryForUpdate(productId);

        inventory.setAvailableStock(request.getAvailableStock());
        inventory.setUpdatedAt(LocalDateTime.now());

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Transactional(readOnly = true)
    public InventoryResponse getSellerInventory(UUID productId, UUID sellerId, boolean admin) {
        assertSellerOwnsProduct(productId, sellerId, admin);
        return getInventory(productId);
    }

    @Transactional
    public InventoryResponse createSellerInventory(CreateInventoryRequest request, UUID sellerId, boolean admin) {
        assertSellerOwnsProduct(request.getProductId(), sellerId, admin);
        return createInventory(request);
    }

    @Transactional
    public InventoryResponse updateSellerInventory(UUID productId, UpdateInventoryRequest request, UUID sellerId,
            boolean admin) {
        assertSellerOwnsProduct(productId, sellerId, admin);
        return updateInventory(productId, request);
    }

    private void assertSellerOwnsProduct(UUID productId, UUID sellerId, boolean admin) {
        if (!admin) {
            productOwnershipVerifier.assertOwnedBy(productId, sellerId);
        }
    }

    private Inventory getInventoryForUpdate(UUID productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found"));
    }

    private InventoryReservation getReservationForUpdate(UUID reservationId) {
        return inventoryReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inventory reservation not found: " + reservationId
                ));
    }

    private void validateInventoryMutation(UUID productId, Integer quantity) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id is required");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private void validateReservationMutation(UUID productId, Integer quantity, UUID reservationId) {
        validateInventoryMutation(productId, quantity);

        if (reservationId == null) {
            throw new IllegalArgumentException("Reservation id is required");
        }
    }

    private void validateReservationMatches(
            InventoryReservation reservation,
            UUID productId,
            Integer quantity
    ) {
        if (!reservation.getProductId().equals(productId)
                || !reservation.getQuantity().equals(quantity)) {
            throw new IllegalArgumentException(
                    "Inventory reservation does not match the requested product and quantity"
            );
        }
    }

    private void ensureAvailableStock(Inventory inventory, Integer quantity) {
        if (inventory.getAvailableStock() < quantity) {
            throw new IllegalArgumentException("Insufficient stock available");
        }
    }

    private void ensureReservedStock(Inventory inventory, Integer quantity) {
        if (inventory.getReservedStock() < quantity) {
            throw new IllegalArgumentException("Reserved stock is insufficient");
        }
    }

    private void reserve(Inventory inventory, Integer quantity) {
        inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
        inventory.setReservedStock(inventory.getReservedStock() + quantity);
        inventory.setUpdatedAt(LocalDateTime.now());
    }

    private void release(Inventory inventory, Integer quantity) {
        inventory.setReservedStock(inventory.getReservedStock() - quantity);
        inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
        inventory.setUpdatedAt(LocalDateTime.now());
    }

    private void deduct(Inventory inventory, Integer quantity) {
        inventory.setReservedStock(inventory.getReservedStock() - quantity);
        inventory.setUpdatedAt(LocalDateTime.now());
    }
}
