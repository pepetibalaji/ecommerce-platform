package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.entity.*;
import com.ecommerce.inventory.mapper.InventoryMapper;
import com.ecommerce.inventory.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryMapper inventoryMapper;
    private final ProductOwnershipVerifier productOwnershipVerifier;
    private final InventoryStockLedgerRepository stockLedgerRepository;
    private final InventoryReservationAuditRepository reservationAuditRepository;
    private final InventoryEventPublisher inventoryEventPublisher;
    @Value("${inventory.reservation.ttl:PT15M}") private Duration reservationTtl = Duration.ofMinutes(15);

    @Transactional public InventoryResponse reserveStock(UUID productId, Integer quantity, UUID reservationId) {
        validate(productId, quantity, reservationId); Inventory inventory = inventory(productId);
        InventoryReservation existing = inventoryReservationRepository.findByIdForUpdate(reservationId).orElse(null);
        if (existing != null) { matches(existing, productId, quantity); if (existing.getStatus() == InventoryReservationStatus.RESERVED) return inventoryMapper.toResponse(inventory); throw new IllegalArgumentException("Reservation cannot be reserved after " + existing.getStatus()); }
        available(inventory, quantity); reserve(inventory, quantity); Inventory saved = inventoryRepository.save(inventory);
        InventoryReservation reservation = new InventoryReservation(reservationId, productId, quantity, Instant.now().plus(reservationTtl));
        inventoryReservationRepository.save(reservation); reservationAuditRepository.save(new InventoryReservationAudit(reservation, null, "order-service")); return inventoryMapper.toResponse(saved);
    }
    @Transactional public InventoryResponse releaseStock(UUID p, Integer q, UUID id) { return transition(p, q, id, InventoryReservationStatus.RELEASED, "order-service"); }
    @Transactional public InventoryResponse deductStock(UUID p, Integer q, UUID id) { return transition(p, q, id, InventoryReservationStatus.DEDUCTED, "order-service"); }
    @Transactional public boolean releaseExpiredReservation(UUID id) {
        InventoryReservation r = reservation(id); if (r.getStatus() != InventoryReservationStatus.RESERVED || r.getExpiresAt().isAfter(Instant.now())) return false;
        transition(r.getProductId(), r.getQuantity(), id, InventoryReservationStatus.RELEASED, "reservation-expiry-worker");
        inventoryEventPublisher.expired(inventory(r.getProductId()), r.getQuantity()); return true;
    }
    private InventoryResponse transition(UUID p, Integer q, UUID id, InventoryReservationStatus target, String actor) {
        validate(p, q, id); Inventory i = inventory(p); InventoryReservation r = reservation(id); matches(r, p, q);
        if (r.getStatus() == target) return inventoryMapper.toResponse(i);
        if (r.getStatus() != InventoryReservationStatus.RESERVED) throw new IllegalArgumentException("Invalid reservation transition from " + r.getStatus() + " to " + target);
        reserved(i, q); InventoryReservationStatus from = r.getStatus(); if (target == InventoryReservationStatus.RELEASED) { release(i, q); r.release(); } else { deduct(i, q); r.deduct(); }
        Inventory saved = inventoryRepository.save(i); inventoryReservationRepository.save(r); reservationAuditRepository.save(new InventoryReservationAudit(r, from, actor)); return inventoryMapper.toResponse(saved);
    }
    @Transactional(readOnly = true) public InventoryResponse getInventory(UUID productId) { return inventoryRepository.findByProductId(productId).map(inventoryMapper::toResponse).orElseThrow(() -> new ResourceNotFoundException("Inventory not found")); }
    @Transactional public InventoryResponse createInventory(CreateInventoryRequest r) { if (inventoryRepository.existsByProductId(r.getProductId())) throw new ResourceAlreadyExistsException("Inventory already exists"); Inventory i = Inventory.builder().id(UUID.randomUUID()).productId(r.getProductId()).availableStock(r.getAvailableStock()).reservedStock(0).updatedAt(Instant.now()).build(); return inventoryMapper.toResponse(inventoryRepository.save(i)); }
    @Transactional public InventoryResponse createInitialInventory(UUID p, UUID s) { if (p == null) throw new IllegalArgumentException("Product id is required"); inventoryRepository.insertInitialIfAbsent(UUID.randomUUID(), p, s); return getInventory(p); }
    @Transactional public InventoryResponse adjustStock(UUID p, StockAdjustmentRequest r, String actor) { if (r.getAdjustment() == null || r.getAdjustment() == 0) throw new IllegalArgumentException("Adjustment must not be zero"); Inventory i = inventory(p); long value = (long) i.getAvailableStock() + r.getAdjustment(); if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Adjustment would violate available stock invariant"); i.setAvailableStock((int) value); i.setUpdatedAt(Instant.now()); Inventory saved = inventoryRepository.save(i); stockLedgerRepository.save(new InventoryStockLedger(i, r.getAdjustment(), r.getReason(), actor, r.getReferenceId())); return inventoryMapper.toResponse(saved); }
    @Transactional(readOnly = true) public InventoryResponse getSellerInventory(UUID p, UUID s, boolean a) { owns(p,s,a); return getInventory(p); }
    @Transactional public InventoryResponse createSellerInventory(CreateInventoryRequest r, UUID s, boolean a) { owns(r.getProductId(),s,a); InventoryResponse response=createInventory(r); Inventory i=inventory(r.getProductId()); i.setSellerId(s); inventoryRepository.save(i); return response; }
    @Transactional public InventoryResponse adjustSellerStock(UUID p, StockAdjustmentRequest r, UUID s, boolean a) { owns(p,s,a); return adjustStock(p,r,"seller:"+s); }
    private void owns(UUID p, UUID s, boolean a) { if (!a) productOwnershipVerifier.assertOwnedBy(p,s); }
    private Inventory inventory(UUID p) { return inventoryRepository.findByProductIdForUpdate(p).orElseThrow(() -> new ResourceNotFoundException("Inventory not found")); }
    private InventoryReservation reservation(UUID id) { return inventoryReservationRepository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Inventory reservation not found")); }
    private void validate(UUID p, Integer q, UUID id) { if (p == null || id == null) throw new IllegalArgumentException("Product id and reservation id are required"); if (q == null || q <= 0) throw new IllegalArgumentException("Quantity must be greater than zero"); }
    private void matches(InventoryReservation r, UUID p, Integer q) { if (!r.getProductId().equals(p) || !r.getQuantity().equals(q)) throw new IllegalArgumentException("Reservation mismatch"); }
    private void available(Inventory i,int q) { if (!i.isProductActive()) throw new IllegalArgumentException("Product is unavailable for new reservations"); if (i.getAvailableStock()<q) throw new IllegalArgumentException("Insufficient stock available"); }
    private void reserved(Inventory i,int q) { if (i.getReservedStock()<q) throw new IllegalArgumentException("Reserved stock is insufficient"); }
    private void reserve(Inventory i,int q) { i.setAvailableStock(i.getAvailableStock()-q); i.setReservedStock(i.getReservedStock()+q); i.setUpdatedAt(Instant.now()); }
    private void release(Inventory i,int q) { i.setReservedStock(i.getReservedStock()-q); i.setAvailableStock(i.getAvailableStock()+q); i.setUpdatedAt(Instant.now()); }
    private void deduct(Inventory i,int q) { i.setReservedStock(i.getReservedStock()-q); i.setUpdatedAt(Instant.now()); }
}
