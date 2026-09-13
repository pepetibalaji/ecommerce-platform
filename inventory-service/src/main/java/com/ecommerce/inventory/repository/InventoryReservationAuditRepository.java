package com.ecommerce.inventory.repository;
import com.ecommerce.inventory.entity.InventoryReservationAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryReservationAuditRepository extends JpaRepository<InventoryReservationAudit, UUID> { }
