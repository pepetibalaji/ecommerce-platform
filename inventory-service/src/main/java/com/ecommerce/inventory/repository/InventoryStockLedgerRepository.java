package com.ecommerce.inventory.repository;
import com.ecommerce.inventory.entity.InventoryStockLedger;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryStockLedgerRepository extends JpaRepository<InventoryStockLedger, UUID> { }
