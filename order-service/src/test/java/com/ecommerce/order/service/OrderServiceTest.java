package com.ecommerce.order.service;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.payment.PaymentFailedEvent;
import com.ecommerce.common.events.payment.PaymentSuccessEvent;
import com.ecommerce.common.events.payment.PaymentRefundCompletedEvent;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.ShippingAddressRequest;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.InventoryReleaseReason;
import com.ecommerce.order.grpc.InventoryGrpcClient;
import com.ecommerce.order.catalog.ProductSellerClient;
import com.ecommerce.order.kafka.OrderEventPublisher;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderProcessedEventRepository;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import com.ecommerce.proto.inventory.InventoryDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderProcessedEventRepository orderProcessedEventRepository;

    @Mock
    private PaymentOutcomeMetrics paymentOutcomeMetrics;

    @Mock
    private InventoryReleaseOutboxService inventoryReleaseOutboxService;

    @Mock
    private ProductSellerClient productSellerClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        /*
         * @Value fields are not injected in pure Mockito tests.
         * This keeps resolveCurrency(null) working.
         */
        ReflectionTestUtils.setField(orderService, "defaultCurrency", "INR");
        // Only order-creation tests invoke the catalog client; keep the shared fixture strict
        // for every other collaborator while avoiding unrelated-test stubbing failures.
        lenient().when(productSellerClient.getSellerId(productId)).thenReturn(UUID.randomUUID());
    }

    @Test
    void createOrder_shouldReserveStockSaveOrderAndPublishCurrencyInEvent() {
        CreateOrderRequest request =
                createOrderRequest(2, new BigDecimal("100.00"), "INR");

        InventoryDetails inventoryDetails = InventoryDetails.newBuilder()
                .setProductId(productId.toString())
                .setAvailableStock(10)
                .setReservedStock(0)
                .build();

        when(inventoryGrpcClient.getInventory(productId))
                .thenReturn(inventoryDetails);

        doNothing()
                .when(inventoryGrpcClient)
                .reserveStock(eq(productId), eq(2), any(UUID.class));

        UUID orderId =
                UUID.randomUUID();

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(orderId);

                    LocalDateTime now = LocalDateTime.now();
                    order.setCreatedAt(now);
                    order.setUpdatedAt(now);

                    return order;
                });

        OrderResponse response =
                orderService.createOrder(userId, request);

        assertThat(response.getId())
                .isEqualTo(orderId);

        assertThat(response.getUserId())
                .isEqualTo(userId);

        assertThat(response.getStatus())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(response.getCurrency())
                .isEqualTo("INR");

        assertThat(response.getShippingAddress())
                .isNotNull();

        assertThat(response.getItems())
                .hasSize(1);

        assertThat(response.getTotalAmount())
                .isEqualByComparingTo("200.00");

        verify(inventoryGrpcClient)
                .getInventory(productId);

        verify(inventoryGrpcClient)
                .reserveStock(eq(productId), eq(2), any(UUID.class));

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository)
                .save(orderCaptor.capture());

        Order savedOrder =
                orderCaptor.getValue();

        assertThat(savedOrder.getCurrency())
                .isEqualTo("INR");

        assertThat(savedOrder.getShippingRecipientName())
                .isEqualTo("Amit Kumar");

        assertThat(savedOrder.getShippingCity())
                .isEqualTo("Bengaluru");

        assertThat(savedOrder.getShippingCountry())
                .isEqualTo("IN");

        UUID reservationId = savedOrder.getItems().getFirst().getInventoryReservationId();

        assertThat(reservationId).isNotNull();

        verify(inventoryGrpcClient)
                .reserveStock(productId, 2, reservationId);

        ArgumentCaptor<OrderCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCreatedEvent.class);

        verify(orderEventPublisher)
                .publishOrderCreated(eventCaptor.capture());

        OrderCreatedEvent event =
                eventCaptor.getValue();

        assertThat(event.getOrderId())
                .isEqualTo(orderId);

        assertThat(event.getUserId())
                .isEqualTo(userId);

        assertThat(event.getTotalAmount())
                .isEqualByComparingTo("200.00");

        assertThat(event.getCurrency())
                .isEqualTo("INR");

        assertThat(event.getItems())
                .hasSize(1);
    }

    @Test
    void createOrder_shouldDefaultCurrencyWhenMissing() {
        CreateOrderRequest request =
                createOrderRequest(2, new BigDecimal("100.00"), null);

        InventoryDetails inventoryDetails = InventoryDetails.newBuilder()
                .setProductId(productId.toString())
                .setAvailableStock(10)
                .setReservedStock(0)
                .build();

        when(inventoryGrpcClient.getInventory(productId))
                .thenReturn(inventoryDetails);

        doNothing()
                .when(inventoryGrpcClient)
                .reserveStock(eq(productId), eq(2), any(UUID.class));

        UUID orderId =
                UUID.randomUUID();

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(orderId);

                    LocalDateTime now = LocalDateTime.now();
                    order.setCreatedAt(now);
                    order.setUpdatedAt(now);

                    return order;
                });

        OrderResponse response =
                orderService.createOrder(userId, request);

        assertThat(response.getCurrency())
                .isEqualTo("INR");

        ArgumentCaptor<OrderCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCreatedEvent.class);

        verify(orderEventPublisher)
                .publishOrderCreated(eventCaptor.capture());

        assertThat(eventCaptor.getValue().getCurrency())
                .isEqualTo("INR");
    }

    @Test
    void createOrder_shouldFailWhenShippingAddressMissing() {
        CreateOrderItemRequest item =
                createOrderItemRequest(2, new BigDecimal("100.00"));

        CreateOrderRequest request =
                new CreateOrderRequest();

        request.setCurrency("INR");
        request.setItems(List.of(item));

        assertThrows(
                BadRequestException.class,
                () -> orderService.createOrder(userId, request)
        );

        verify(inventoryGrpcClient, never())
                .getInventory(any());

        verify(inventoryGrpcClient, never())
                .reserveStock(any(), anyInt(), any(UUID.class));

        verify(orderRepository, never())
                .save(any(Order.class));

        verify(orderEventPublisher, never())
                .publishOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_shouldFailWhenStockInsufficient() {
        CreateOrderRequest request =
                createOrderRequest(20, new BigDecimal("100.00"), "INR");

        InventoryDetails inventoryDetails = InventoryDetails.newBuilder()
                .setProductId(productId.toString())
                .setAvailableStock(10)
                .setReservedStock(0)
                .build();

        when(inventoryGrpcClient.getInventory(productId))
                .thenReturn(inventoryDetails);

        assertThrows(
                BadRequestException.class,
                () -> orderService.createOrder(userId, request)
        );

        verify(inventoryGrpcClient, never())
                .reserveStock(any(), anyInt(), any(UUID.class));

        verify(orderRepository, never())
                .save(any(Order.class));

        verify(orderEventPublisher, never())
                .publishOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    void cancelOrder_shouldReleaseStockAndCancel() {
        UUID orderId =
                UUID.randomUUID();

        Order order =
                existingOrder(orderId, OrderStatus.CONFIRMED);

        OrderItem item =
                existingOrderItem(order);

        order.getItems().add(item);

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response =
                orderService.cancelOrder(userId, orderId);

        assertThat(response.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(response.getCurrency())
                .isEqualTo("INR");

        verify(inventoryReleaseOutboxService)
                .enqueueFor(order, InventoryReleaseReason.CANCELLED);

        verify(inventoryGrpcClient, never())
                .releaseStock(any(), anyInt(), any(UUID.class));

        verify(orderRepository)
                .save(order);
    }

    @Test
    void getOrderById_shouldThrowForDifferentUser() {
        UUID orderId =
                UUID.randomUUID();

        Order order =
                existingOrder(orderId, OrderStatus.PENDING);

        order.setUserId(UUID.randomUUID());

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(order));

        assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.getOrderById(userId, orderId)
        );
    }

    @Test
    void getMyOrders_shouldReturnPagedOrders() {
        Order order =
                existingOrder(UUID.randomUUID(), OrderStatus.PENDING);

        when(orderRepository.findByUserId(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(order)));

        var page =
                orderService.getMyOrders(
                        userId,
                        PageRequest.of(0, 10),
                        null
                );

        assertThat(page.getContent())
                .hasSize(1);

        assertThat(page.getContent().get(0).getUserId())
                .isEqualTo(userId);

        assertThat(page.getContent().get(0).getCurrency())
                .isEqualTo("INR");
    }

    @Test
    void updateOrderStatus_shouldUpdateStatus() {
        UUID orderId =
                UUID.randomUUID();

        Order order =
                existingOrder(orderId, OrderStatus.PENDING);

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrderStatusRequest request =
                new UpdateOrderStatusRequest();

        request.setStatus(OrderStatus.CONFIRMED);

        OrderResponse response =
                orderService.updateOrderStatus(orderId, request);

        assertThat(response.getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        assertThat(response.getCurrency())
                .isEqualTo("INR");

        verify(orderRepository)
                .save(order);
    }

    @Test
    void handlePaymentSuccess_shouldConfirmPendingOrderAndRecordEvent() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                paymentId, orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "transaction-1", "correlation-1", "trace-1");
        Order order = existingOrder(orderId, OrderStatus.PENDING);

        order.getItems().add(existingOrderItem(order));

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        orderService.handlePaymentSuccess(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
        assertThat(order.getPaymentConfirmedAt()).isNotNull();
        verify(orderRepository).save(order);
        verify(orderProcessedEventRepository).save(any());
    }

    @Test
    void handlePaymentFailure_shouldMarkPendingOrderAsPaymentFailedAndRecordEvent() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent(
                paymentId, orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "DECLINED", "Card was declined", "correlation-1", "trace-1");
        Order order = existingOrder(orderId, OrderStatus.PENDING);

        order.getItems().add(existingOrderItem(order));

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        orderService.handlePaymentFailure(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
        assertThat(order.getPaymentFailedAt()).isNotNull();
        assertThat(order.getPaymentFailureReason()).isEqualTo("Card was declined");
        verify(orderRepository).save(order);
        verify(inventoryReleaseOutboxService)
                .enqueueFor(order, InventoryReleaseReason.PAYMENT_FAILED);
        verify(orderProcessedEventRepository).save(any());
    }

    @Test
    void handlePaymentFailure_shouldRemainRetryableWhenReservationIdIsMissing() {
        UUID orderId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "DECLINED", "Card was declined", "correlation-1", "trace-1");
        Order order = existingOrder(orderId, OrderStatus.PENDING);
        OrderItem item = existingOrderItem(order);
        item.setInventoryReservationId(null);
        order.getItems().add(item);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);
        doThrow(new IllegalStateException("Missing inventory reservation id"))
                .when(inventoryReleaseOutboxService)
                .enqueueFor(order, InventoryReleaseReason.PAYMENT_FAILED);

        assertThrows(IllegalStateException.class, () -> orderService.handlePaymentFailure(event));

        verify(orderRepository, never()).save(order);
        verify(orderProcessedEventRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_shouldQueueInventoryReleaseWhenCancelling() {
        UUID orderId = UUID.randomUUID();
        Order order = existingOrder(orderId, OrderStatus.PENDING);
        order.getItems().add(existingOrderItem(order));

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);

        OrderResponse response = orderService.updateOrderStatus(orderId, request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryReleaseOutboxService)
                .enqueueFor(order, InventoryReleaseReason.CANCELLED);
    }

    @Test
    void handlePaymentSuccess_shouldIgnoreDuplicateEvent() {
        UUID orderId = UUID.randomUUID();
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(), orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "transaction-1", "correlation-1", "trace-1");
        Order order = existingOrder(orderId, OrderStatus.PENDING);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(true);

        orderService.handlePaymentSuccess(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(order);
        verifyNoInteractions(inventoryReleaseOutboxService);
        verify(orderProcessedEventRepository, never()).save(any());
    }

    @Test
    void handlePaymentFailure_shouldIgnoreLateFailureForConfirmedOrder() {
        UUID orderId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "DECLINED", "Card was declined", "correlation-1", "trace-1");
        Order order = existingOrder(orderId, OrderStatus.CONFIRMED);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        orderService.handlePaymentFailure(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(order);
        verifyNoInteractions(inventoryReleaseOutboxService);
        verify(orderProcessedEventRepository, times(1)).save(any());
    }

    @Test
    void handlePaymentSuccess_shouldIgnoreLateSuccessForCancelledOrder() {
        UUID orderId = UUID.randomUUID();
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(), orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "transaction-1", "correlation-1", "trace-1");
        Order order = existingOrder(orderId, OrderStatus.CANCELLED);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        orderService.handlePaymentSuccess(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(order);
        verifyNoInteractions(inventoryReleaseOutboxService);
        verify(orderProcessedEventRepository).save(any());
    }

    @Test
    void handlePaymentSuccess_shouldFailForUnknownOrder() {
        UUID orderId = UUID.randomUUID();
        PaymentSuccessEvent event = new PaymentSuccessEvent(
                UUID.randomUUID(), orderId, userId, new BigDecimal("100.00"), "INR",
                "SANDBOX", "transaction-1", "correlation-1", "trace-1");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.handlePaymentSuccess(event));
        verifyNoInteractions(inventoryReleaseOutboxService);
        verify(orderProcessedEventRepository, never()).save(any());
    }

    private CreateOrderRequest createOrderRequest(
            int quantity,
            BigDecimal price,
            String currency
    ) {
        CreateOrderRequest request =
                new CreateOrderRequest();

        request.setCurrency(currency);
        request.setShippingAddress(shippingAddressRequest());
        request.setItems(List.of(createOrderItemRequest(quantity, price)));

        return request;
    }

    private CreateOrderItemRequest createOrderItemRequest(
            int quantity,
            BigDecimal price
    ) {
        CreateOrderItemRequest item =
                new CreateOrderItemRequest();

        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price);

        return item;
    }

    private ShippingAddressRequest shippingAddressRequest() {
        ShippingAddressRequest address =
                new ShippingAddressRequest();

        address.setRecipientName("Amit Kumar");
        address.setPhone("+919999999999");
        address.setLine1("Flat 101, Green Residency");
        address.setLine2("Near Metro Station");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPostalCode("560001");
        address.setCountry("IN");

        return address;
    }

    @Test
    void fullRefund_shouldQueueOneReleaseAndMarkOrderRefunded() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Order order = existingOrder(orderId, OrderStatus.CONFIRMED);
        order.getItems().add(existingOrderItem(order));
        PaymentRefundCompletedEvent event = refundEvent(orderId, paymentId, true);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        orderService.handleRefundCompleted(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(inventoryReleaseOutboxService).enqueueFor(order, InventoryReleaseReason.FULL_REFUND);
        verify(orderProcessedEventRepository).save(any());
    }

    @Test
    void duplicateFullRefund_shouldNotQueueAnotherRelease() {
        UUID orderId = UUID.randomUUID();
        PaymentRefundCompletedEvent event = refundEvent(orderId, UUID.randomUUID(), true);
        Order order = existingOrder(orderId, OrderStatus.CONFIRMED);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(true);

        orderService.handleRefundCompleted(event);

        verifyNoInteractions(inventoryReleaseOutboxService);
        verify(orderProcessedEventRepository, never()).save(any());
    }

    @Test
    void fullRefundForNonConfirmedOrder_shouldRouteToFulfilmentWithoutRelease() {
        UUID orderId = UUID.randomUUID();
        PaymentRefundCompletedEvent event = refundEvent(orderId, UUID.randomUUID(), true);
        Order order = existingOrder(orderId, OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderProcessedEventRepository.existsByEventId(event.getEventId())).thenReturn(false);

        orderService.handleRefundCompleted(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUIRES_FULFILMENT_REVIEW);
        verifyNoInteractions(inventoryReleaseOutboxService);
    }

    private PaymentRefundCompletedEvent refundEvent(UUID orderId, UUID paymentId, boolean fullRefund) {
        BigDecimal total = fullRefund ? new BigDecimal("200.00") : new BigDecimal("50.00");
        return new PaymentRefundCompletedEvent(UUID.randomUUID(), paymentId, orderId, total, total,
                new BigDecimal("200.00"), "INR", null, null);
    }

    private Order existingOrder(
            UUID orderId,
            OrderStatus status
    ) {
        LocalDateTime now =
                LocalDateTime.now();

        Order order =
                new Order();

        order.setId(orderId);
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setCurrency("INR");
        order.setStatus(status);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        order.setShippingAddressId(null);
        order.setShippingRecipientName("Amit Kumar");
        order.setShippingPhone("+919999999999");
        order.setShippingLine1("Flat 101, Green Residency");
        order.setShippingLine2("Near Metro Station");
        order.setShippingCity("Bengaluru");
        order.setShippingState("Karnataka");
        order.setShippingPostalCode("560001");
        order.setShippingCountry("IN");

        return order;
    }

    private OrderItem existingOrderItem(Order order) {
        OrderItem item =
                new OrderItem();

        item.setId(UUID.randomUUID());
        item.setOrder(order);
        item.setProductId(productId);
        item.setInventoryReservationId(UUID.randomUUID());
        item.setQuantity(2);
        item.setPrice(new BigDecimal("100.00"));

        return item;
    }
}
