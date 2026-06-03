package com.ecommerce.inventory.grpc;

import com.ecommerce.inventory.service.InventoryService;

import com.ecommerce.proto.inventory.*;

import io.grpc.stub.StreamObserver;

import lombok.RequiredArgsConstructor;

import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService
        extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void getInventory(
            GetInventoryRequest request,
            StreamObserver<InventoryDetails> responseObserver
    ) {

        com.ecommerce.inventory.dto.InventoryResponse inventory =
                inventoryService.getInventory(
                        UUID.fromString(
                                request.getProductId()
                        )
                );

        InventoryDetails response =
                InventoryDetails.newBuilder()
                        .setProductId(
                                inventory.getProductId().toString()
                        )
                        .setAvailableStock(
                                inventory.getAvailableStock()
                        )
                        .setReservedStock(
                                inventory.getReservedStock()
                        )
                        .build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(
            ReserveStockRequest request,
            StreamObserver<com.ecommerce.proto.inventory.InventoryResponse> responseObserver
    ) {

        inventoryService.reserveStock(
                UUID.fromString(
                        request.getProductId()
                ),
                request.getQuantity()
        );

        com.ecommerce.proto.inventory.InventoryResponse response =
                com.ecommerce.proto.inventory.InventoryResponse
                        .newBuilder()
                        .setSuccess(true)
                        .setMessage(
                                "Stock reserved successfully"
                        )
                        .build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }

    @Override
    public void releaseStock(
            ReleaseStockRequest request,
            StreamObserver<com.ecommerce.proto.inventory.InventoryResponse> responseObserver
    ) {

        inventoryService.releaseStock(
                UUID.fromString(
                        request.getProductId()
                ),
                request.getQuantity()
        );

        com.ecommerce.proto.inventory.InventoryResponse response =
                com.ecommerce.proto.inventory.InventoryResponse
                        .newBuilder()
                        .setSuccess(true)
                        .setMessage(
                                "Stock released successfully"
                        )
                        .build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }

    @Override
    public void deductStock(
            DeductStockRequest request,
            StreamObserver<com.ecommerce.proto.inventory.InventoryResponse> responseObserver
    ) {

        inventoryService.deductStock(
                UUID.fromString(
                        request.getProductId()
                ),
                request.getQuantity()
        );

        com.ecommerce.proto.inventory.InventoryResponse response =
                com.ecommerce.proto.inventory.InventoryResponse
                        .newBuilder()
                        .setSuccess(true)
                        .setMessage(
                                "Stock deducted successfully"
                        )
                        .build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }
}
