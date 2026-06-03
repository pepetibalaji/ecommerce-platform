package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository
        extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);
}