package com.ecommerce.order.grpc;

import java.util.UUID;

import com.ecommerce.proto.inventory.InventoryDetails;

public interface InventoryGrpcClient {

    InventoryDetails getInventory(UUID productId);

    void reserveStock(UUID productId, int quantity);

    void releaseStock(UUID productId, int quantity);
}