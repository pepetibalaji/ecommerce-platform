package com.ecommerce.inventory.grpc;

import com.ecommerce.inventory.dto.InventoryResponse;

import com.ecommerce.inventory.service.InventoryService;

import com.ecommerce.proto.inventory.*;

import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcServiceTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private StreamObserver<InventoryDetails> inventoryObserver;

    @Mock
    private StreamObserver<com.ecommerce.proto.inventory.InventoryResponse> responseObserver;

    @InjectMocks
    private InventoryGrpcService inventoryGrpcService;

    private UUID productId;

    @BeforeEach
    void setUp() {

        productId = UUID.randomUUID();
    }

    @Test
    void shouldGetInventory() {

        InventoryResponse response =
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(100)
                        .reservedStock(0)
                        .build();

        when(
                inventoryService.getInventory(productId)
        ).thenReturn(response);

        GetInventoryRequest request =
                GetInventoryRequest.newBuilder()
                        .setProductId(productId.toString())
                        .build();

        inventoryGrpcService.getInventory(
                request,
                inventoryObserver
        );

        verify(inventoryObserver)
                .onNext(any(InventoryDetails.class));

        verify(inventoryObserver)
                .onCompleted();
    }

    @Test
    void shouldReserveStock() {

        when(
                inventoryService.reserveStock(
                        any(UUID.class),
                        eq(5)
                )
        ).thenReturn(
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(95)
                        .reservedStock(5)
                        .build()
        );

        ReserveStockRequest request =
                ReserveStockRequest.newBuilder()
                        .setProductId(productId.toString())
                        .setQuantity(5)
                        .build();

        inventoryGrpcService.reserveStock(
                request,
                responseObserver
        );

        verify(responseObserver)
                .onNext(
                        any(
                                com.ecommerce.proto.inventory.InventoryResponse.class
                        )
                );

        verify(responseObserver)
                .onCompleted();
    }

    @Test
    void shouldForwardReservationIdForReservationAwareReserve() {
        UUID reservationId = UUID.randomUUID();

        when(inventoryService.reserveStock(productId, 5, reservationId))
                .thenReturn(InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(95)
                        .reservedStock(5)
                        .build());

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(productId.toString())
                .setQuantity(5)
                .setReservationId(reservationId.toString())
                .build();

        inventoryGrpcService.reserveStock(request, responseObserver);

        verify(inventoryService).reserveStock(productId, 5, reservationId);
        verify(responseObserver).onCompleted();
    }

    @Test
    void shouldReleaseStock() {

        when(
                inventoryService.releaseStock(
                        any(UUID.class),
                        eq(5)
                )
        ).thenReturn(
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(100)
                        .reservedStock(0)
                        .build()
        );

        ReleaseStockRequest request =
                ReleaseStockRequest.newBuilder()
                        .setProductId(productId.toString())
                        .setQuantity(5)
                        .build();

        inventoryGrpcService.releaseStock(
                request,
                responseObserver
        );

        verify(responseObserver)
                .onNext(
                        any(
                                com.ecommerce.proto.inventory.InventoryResponse.class
                        )
                );

        verify(responseObserver)
                .onCompleted();
    }

    @Test
    void shouldDeductStock() {

        when(
                inventoryService.deductStock(
                        any(UUID.class),
                        eq(5)
                )
        ).thenReturn(
                InventoryResponse.builder()
                        .productId(productId)
                        .availableStock(95)
                        .reservedStock(0)
                        .build()
        );

        DeductStockRequest request =
                DeductStockRequest.newBuilder()
                        .setProductId(productId.toString())
                        .setQuantity(5)
                        .build();

        inventoryGrpcService.deductStock(
                request,
                responseObserver
        );

        verify(responseObserver)
                .onNext(
                        any(
                                com.ecommerce.proto.inventory.InventoryResponse.class
                        )
                );

        verify(responseObserver)
                .onCompleted();
    }
}
