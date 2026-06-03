package com.ecommerce.inventory.grpc;

import com.ecommerce.inventory.dto.InventoryResponse;

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

        InventoryResponse inventory =
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
}