package com.ecommerce.order.grpc;

import java.util.UUID;

import com.ecommerce.proto.inventory.GetInventoryRequest;
import com.ecommerce.proto.inventory.InventoryDetails;
import com.ecommerce.proto.inventory.InventoryResponse;
import com.ecommerce.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.proto.inventory.ReleaseStockRequest;
import com.ecommerce.proto.inventory.ReserveStockRequest;
import org.springframework.stereotype.Service;

@Service
public class InventoryGrpcClientImpl implements InventoryGrpcClient {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public InventoryGrpcClientImpl(
            InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub
    ) {
        this.inventoryStub = inventoryStub;
    }

    @Override
    public InventoryDetails getInventory(UUID productId) {
        return inventoryStub.getInventory(
                GetInventoryRequest.newBuilder()
                        .setProductId(productId.toString())
                        .build()
        );
    }

    @Override
    public void reserveStock(UUID productId, int quantity) {
        InventoryResponse response = inventoryStub.reserveStock(
                ReserveStockRequest.newBuilder()
                        .setProductId(productId.toString())
                        .setQuantity(quantity)
                        .build()
        );

        if (!response.getSuccess()) {
            throw new IllegalStateException(response.getMessage());
        }
    }

    @Override
    public void releaseStock(UUID productId, int quantity) {
        InventoryResponse response = inventoryStub.releaseStock(
                ReleaseStockRequest.newBuilder()
                        .setProductId(productId.toString())
                        .setQuantity(quantity)
                        .build()
        );

        if (!response.getSuccess()) {
            throw new IllegalStateException(response.getMessage());
        }
    }
}