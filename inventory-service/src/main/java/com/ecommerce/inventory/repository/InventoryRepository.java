package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entity.Inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository
        extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") UUID productId);

    boolean existsByProductId(UUID productId);

    /** PostgreSQL arbitrates concurrent provisioning, including legacy and lifecycle consumers. */
    @Modifying
    @Query(value = """
            INSERT INTO inventory (id, product_id, seller_id, available_stock, reserved_stock, updated_at)
            VALUES (:id, :productId, :sellerId, 0, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (product_id) DO NOTHING
            """, nativeQuery = true)
    int insertInitialIfAbsent(@Param("id") UUID id, @Param("productId") UUID productId,
                             @Param("sellerId") UUID sellerId);

    /** A newer snapshot changes catalogue metadata only, preserving stock and reservations. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO inventory (id, product_id, seller_id, available_stock, reserved_stock,
                                   updated_at, product_active, product_version, last_product_event_id)
            VALUES (:id, :productId, :sellerId, 0, 0, CURRENT_TIMESTAMP, :active, :version, :eventId)
            ON CONFLICT (product_id) DO UPDATE SET
                seller_id = EXCLUDED.seller_id,
                product_active = EXCLUDED.product_active,
                product_version = EXCLUDED.product_version,
                last_product_event_id = EXCLUDED.last_product_event_id,
                updated_at = CURRENT_TIMESTAMP
            WHERE inventory.product_version < EXCLUDED.product_version
            """, nativeQuery = true)
    int applyProductSnapshot(@Param("id") UUID id, @Param("productId") UUID productId,
                             @Param("sellerId") UUID sellerId, @Param("active") boolean active,
                             @Param("version") long version, @Param("eventId") UUID eventId);
}
