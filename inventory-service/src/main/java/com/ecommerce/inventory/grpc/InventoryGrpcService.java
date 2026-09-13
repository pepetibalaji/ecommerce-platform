package com.ecommerce.inventory.grpc;

import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.proto.inventory.DeductStockRequest;
import com.ecommerce.proto.inventory.GetInventoryRequest;
import com.ecommerce.proto.inventory.InventoryDetails;
import com.ecommerce.proto.inventory.InventoryResponse;
import com.ecommerce.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.proto.inventory.ReleaseStockRequest;
import com.ecommerce.proto.inventory.ReserveStockRequest;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;

    @Override
    public void getInventory(
            GetInventoryRequest request,
            StreamObserver<InventoryDetails> responseObserver
    ) {
        com.ecommerce.inventory.dto.InventoryResponse inventory = inventoryService.getInventory(
                UUID.fromString(request.getProductId())
        );

        InventoryDetails response = InventoryDetails.newBuilder()
                .setProductId(inventory.getProductId().toString())
                .setAvailableStock(inventory.getAvailableStock())
                .setReservedStock(inventory.getReservedStock())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(
            ReserveStockRequest request,
            StreamObserver<InventoryResponse> responseObserver
    ) {
        UUID productId = UUID.fromString(request.getProductId());
        UUID reservationId = requiredReservationId(request.getReservationId());
        inventoryService.reserveStock(productId, request.getQuantity(), reservationId);

        respond(responseObserver, "Stock reserved successfully");
    }

    @Override
    public void releaseStock(
            ReleaseStockRequest request,
            StreamObserver<InventoryResponse> responseObserver
    ) {
        UUID productId = UUID.fromString(request.getProductId());
        UUID reservationId = requiredReservationId(request.getReservationId());
        inventoryService.releaseStock(productId, request.getQuantity(), reservationId);

        respond(responseObserver, "Stock released successfully");
    }

    @Override
    public void deductStock(
            DeductStockRequest request,
            StreamObserver<InventoryResponse> responseObserver
    ) {
        UUID productId = UUID.fromString(request.getProductId());
        UUID reservationId = requiredReservationId(request.getReservationId());
        inventoryService.deductStock(productId, request.getQuantity(), reservationId);

        respond(responseObserver, "Stock deducted successfully");
    }

    private UUID requiredReservationId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reservationId is required and must be a UUID");
        }
        return UUID.fromString(value);
    }

    private void respond(StreamObserver<InventoryResponse> responseObserver, String message) {
        responseObserver.onNext(InventoryResponse.newBuilder()
                .setSuccess(true)
                .setMessage(message)
                .build());
        responseObserver.onCompleted();
    }
}
