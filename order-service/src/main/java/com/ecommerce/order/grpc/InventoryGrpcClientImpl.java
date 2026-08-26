package com.ecommerce.order.grpc;

import java.util.UUID;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.grpc.exception.GrpcExceptionMapper;
import com.ecommerce.common.grpc.factory.GrpcClientFactory;
import com.ecommerce.proto.inventory.GetInventoryRequest;
import com.ecommerce.proto.inventory.InventoryDetails;
import com.ecommerce.proto.inventory.InventoryResponse;
import com.ecommerce.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.proto.inventory.ReleaseStockRequest;
import com.ecommerce.proto.inventory.ReserveStockRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

@Service
public class InventoryGrpcClientImpl implements InventoryGrpcClient {

    private static final String CLIENT_NAME = "inventory";

    private final GrpcClientFactory grpcClientFactory;

    public InventoryGrpcClientImpl(GrpcClientFactory grpcClientFactory) {
        this.grpcClientFactory = grpcClientFactory;
    }

    @Override
    public InventoryDetails getInventory(UUID productId) {
        try {
            GetInventoryRequest request = GetInventoryRequest.newBuilder()
                    .setProductId(productId.toString())
                    .build();

            return inventoryStub().getInventory(request);
        } catch (StatusRuntimeException exception) {
            throw GrpcExceptionMapper.map(CLIENT_NAME, exception);
        }
    }

    @Override
    public void reserveStock(UUID productId, int quantity) {
        try {
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                    .setProductId(productId.toString())
                    .setQuantity(quantity)
                    .build();

            InventoryResponse response = inventoryStub().reserveStock(request);

            if (!response.getSuccess()) {
                throw new BadRequestException(response.getMessage());
            }
        } catch (StatusRuntimeException exception) {
            throw GrpcExceptionMapper.map(CLIENT_NAME, exception);
        }
    }

    @Override
    public void reserveStock(UUID productId, int quantity, UUID reservationId) {
        try {
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                    .setProductId(productId.toString())
                    .setQuantity(quantity)
                    .setReservationId(reservationId.toString())
                    .build();

            InventoryResponse response = inventoryStub().reserveStock(request);

            if (!response.getSuccess()) {
                throw new BadRequestException(response.getMessage());
            }
        } catch (StatusRuntimeException exception) {
            throw GrpcExceptionMapper.map(CLIENT_NAME, exception);
        }
    }

    @Override
    public void releaseStock(UUID productId, int quantity) {
        try {
            ReleaseStockRequest request = ReleaseStockRequest.newBuilder()
                    .setProductId(productId.toString())
                    .setQuantity(quantity)
                    .build();

            InventoryResponse response = inventoryStub().releaseStock(request);

            if (!response.getSuccess()) {
                throw new BadRequestException(response.getMessage());
            }
        } catch (StatusRuntimeException exception) {
            throw GrpcExceptionMapper.map(CLIENT_NAME, exception);
        }
    }

    @Override
    public void releaseStock(UUID productId, int quantity, UUID reservationId) {
        try {
            ReleaseStockRequest request = ReleaseStockRequest.newBuilder()
                    .setProductId(productId.toString())
                    .setQuantity(quantity)
                    .setReservationId(reservationId.toString())
                    .build();

            InventoryResponse response = inventoryStub().releaseStock(request);

            if (!response.getSuccess()) {
                throw new BadRequestException(response.getMessage());
            }
        } catch (StatusRuntimeException exception) {
            throw GrpcExceptionMapper.map(CLIENT_NAME, exception);
        }
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub() {
        return grpcClientFactory.stub(
                CLIENT_NAME,
                InventoryServiceGrpc::newBlockingStub
        );
    }
}
