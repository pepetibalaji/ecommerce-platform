package com.ecommerce.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.event.OrderCreatedEvent;
import com.ecommerce.order.kafka.OrderEventPublisher;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.grpc.InventoryGrpcClient;
import com.ecommerce.proto.inventory.InventoryDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    @Test
    void createOrder_shouldReserveStockAndSaveOrder() {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        InventoryDetails inventoryDetails = InventoryDetails.newBuilder()
                .setProductId(productId.toString())
                .setAvailableStock(10)
                .setReservedStock(0)
                .build();

        when(inventoryGrpcClient.getInventory(productId)).thenReturn(inventoryDetails);
        doNothing().when(inventoryGrpcClient).reserveStock(productId, 2);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("200.00");

        verify(inventoryGrpcClient).getInventory(productId);
        verify(inventoryGrpcClient).reserveStock(productId, 2);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_shouldFailWhenStockInsufficient() {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(20);
        item.setPrice(new BigDecimal("100.00"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));

        InventoryDetails inventoryDetails = InventoryDetails.newBuilder()
                .setProductId(productId.toString())
                .setAvailableStock(10)
                .setReservedStock(0)
                .build();

        when(inventoryGrpcClient.getInventory(productId)).thenReturn(inventoryDetails);

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(userId, request));

        verify(inventoryGrpcClient, never()).reserveStock(any(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_shouldReleaseStockAndCancel() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(productId);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));

        order.getItems().add(item);

        when(orderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(userId, order.getId());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryGrpcClient).releaseStock(productId, 2);
    }

    @Test
    void getOrderById_shouldThrowForDifferentUser() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(order.getId())).thenReturn(java.util.Optional.of(order));

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderById(userId, order.getId()));
    }

    @Test
    void getMyOrders_shouldReturnPagedOrders() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findByUserId(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(order)));

        var page = orderService.getMyOrders(userId, PageRequest.of(0, 10), null);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void updateOrderStatus_shouldUpdateStatus() {
        UUID orderId = UUID.randomUUID();

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CONFIRMED);

        OrderResponse response = orderService.updateOrderStatus(orderId, request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}