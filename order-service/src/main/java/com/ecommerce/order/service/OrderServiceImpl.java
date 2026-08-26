package com.ecommerce.order.service;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.order.OrderItemEvent;
import com.ecommerce.common.events.payment.PaymentFailedEvent;
import com.ecommerce.common.events.payment.PaymentSuccessEvent;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.dto.CreateOrderItemRequest;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.ShippingAddressRequest;
import com.ecommerce.order.dto.ShippingAddressResponse;
import com.ecommerce.order.dto.UpdateOrderStatusRequest;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.entity.InventoryReleaseReason;
import com.ecommerce.order.grpc.InventoryGrpcClient;
import com.ecommerce.order.kafka.OrderEventPublisher;
import com.ecommerce.order.repository.OrderProcessedEventRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.entity.OrderProcessedEvent;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderProcessedEventRepository orderProcessedEventRepository;
    private final PaymentOutcomeMetrics paymentOutcomeMetrics;
    private final InventoryReleaseOutboxService inventoryReleaseOutboxService;

    @Value("${order.default-currency:INR}")
    private String defaultCurrency;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            InventoryGrpcClient inventoryGrpcClient,
            OrderEventPublisher orderEventPublisher,
            OrderProcessedEventRepository orderProcessedEventRepository,
            PaymentOutcomeMetrics paymentOutcomeMetrics,
            InventoryReleaseOutboxService inventoryReleaseOutboxService
    ) {
        this.orderRepository = orderRepository;
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderEventPublisher = orderEventPublisher;
        this.orderProcessedEventRepository = orderProcessedEventRepository;
        this.paymentOutcomeMetrics = paymentOutcomeMetrics;
        this.inventoryReleaseOutboxService = inventoryReleaseOutboxService;
    }

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        validateCreateOrderRequest(request);

        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            Order order = new Order();
            order.setUserId(userId);
            order.setCurrency(resolveCurrency(request.getCurrency()));
            order.setStatus(OrderStatus.PENDING);

            applyShippingAddress(
                    order,
                    request.getShippingAddressId(),
                    request.getShippingAddress()
            );

            BigDecimal totalAmount = BigDecimal.ZERO;

            for (CreateOrderItemRequest itemRequest : request.getItems()) {
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProductId(itemRequest.getProductId());
                item.setInventoryReservationId(UUID.randomUUID());
                item.setQuantity(itemRequest.getQuantity());
                item.setPrice(itemRequest.getPrice());

                order.getItems().add(item);

                totalAmount = totalAmount.add(
                        itemRequest.getPrice()
                                .multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                );
            }

            validateStockAvailability(request);
            reserveStock(order.getItems(), reservedItems);

            order.setTotalAmount(totalAmount);

            Order saved = orderRepository.save(order);

            publishOrderCreatedEvent(saved);

            return toResponse(saved);
        } catch (RuntimeException ex) {
            releaseReservedStock(reservedItems);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(
            UUID userId,
            Pageable pageable,
            OrderStatus status
    ) {
        Page<Order> page = (status == null)
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);

        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        return toResponse(order);
    }

    @Override
    public OrderResponse cancelOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        validateStatusTransition(order.getStatus(), OrderStatus.CANCELLED);

        inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.CANCELLED);

        order.setStatus(OrderStatus.CANCELLED);

        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAdminOrders(Pageable pageable, OrderStatus status) {
        Page<Order> page = (status == null)
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);

        return page.map(this::toResponse);
    }

    @Override
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        validateStatusTransition(order.getStatus(), request.getStatus());

        if (request.getStatus() == OrderStatus.CANCELLED) {
            inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.CANCELLED);
        }

        order.setStatus(request.getStatus());

        return toResponse(orderRepository.save(order));
    }

    @Override
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        validatePaymentEvent(event == null ? null : event.getEventId(),
                event == null ? null : event.getOrderId(),
                event == null ? null : event.getPaymentId());

        Order order = orderRepository.findByIdForUpdate(event.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for payment event: " + event.getOrderId()));

        if (orderProcessedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Ignoring duplicate payment-success event. eventId={}, orderId={}, paymentId={}",
                    event.getEventId(), event.getOrderId(), event.getPaymentId());
            paymentOutcomeMetrics.duplicateIgnored();
            return;
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentId(event.getPaymentId());
            order.setPaymentConfirmedAt(LocalDateTime.now());
            order.setPaymentFailedAt(null);
            order.setPaymentFailureReason(null);
            orderRepository.save(order);
            paymentOutcomeMetrics.orderUpdated("success");
        } else if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("Ignoring late payment-success event. eventId={}, orderId={}, paymentId={}, orderStatus={}",
                    event.getEventId(), event.getOrderId(), event.getPaymentId(), order.getStatus());
            paymentOutcomeMetrics.lateEventIgnored("success");
        }

        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    @Override
    public void handlePaymentFailure(PaymentFailedEvent event) {
        validatePaymentEvent(event == null ? null : event.getEventId(),
                event == null ? null : event.getOrderId(),
                event == null ? null : event.getPaymentId());

        Order order = orderRepository.findByIdForUpdate(event.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for payment event: " + event.getOrderId()));

        if (orderProcessedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Ignoring duplicate payment-failed event. eventId={}, orderId={}, paymentId={}",
                    event.getEventId(), event.getOrderId(), event.getPaymentId());
            paymentOutcomeMetrics.duplicateIgnored();
            return;
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.PAYMENT_FAILED);
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setPaymentId(event.getPaymentId());
            order.setPaymentFailedAt(LocalDateTime.now());
            order.setPaymentFailureReason(event.getFailureReason());
            orderRepository.save(order);
            paymentOutcomeMetrics.orderUpdated("failure");
        } else if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            log.warn("Ignoring late payment-failed event. eventId={}, orderId={}, paymentId={}, orderStatus={}",
                    event.getEventId(), event.getOrderId(), event.getPaymentId(), order.getStatus());
            paymentOutcomeMetrics.lateEventIgnored("failure");
        }

        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    private void validatePaymentEvent(UUID eventId, UUID orderId, UUID paymentId) {
        if (eventId == null || orderId == null || paymentId == null) {
            throw new BadRequestException("Payment outcome event must contain eventId, orderId, and paymentId");
        }
    }

    private void recordProcessedEvent(UUID eventId, String eventType, UUID orderId) {
        orderProcessedEventRepository.save(new OrderProcessedEvent(eventId, eventType, orderId));
    }

    private void validateCreateOrderRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Order request is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order must contain at least one item");
        }

        if (request.getShippingAddress() == null) {
            throw new BadRequestException("Shipping address is required");
        }

        for (CreateOrderItemRequest item : request.getItems()) {
            if (item.getProductId() == null) {
                throw new BadRequestException("Product id is required");
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Quantity must be greater than zero");
            }

            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Price must be greater than zero");
            }
        }
    }

    private String resolveCurrency(String currency) {
        String resolved = (currency == null || currency.isBlank())
                ? defaultCurrency
                : currency;

        resolved = resolved.trim().toUpperCase();

        if (!resolved.matches("^[A-Z]{3}$")) {
            throw new BadRequestException("Currency must be a 3-letter uppercase ISO code, for example INR or USD");
        }

        return resolved;
    }

    private void applyShippingAddress(
            Order order,
            UUID shippingAddressId,
            ShippingAddressRequest address
    ) {
        order.setShippingAddressId(shippingAddressId);
        order.setShippingRecipientName(address.getRecipientName());
        order.setShippingPhone(address.getPhone());
        order.setShippingLine1(address.getLine1());
        order.setShippingLine2(address.getLine2());
        order.setShippingCity(address.getCity());
        order.setShippingState(address.getState());
        order.setShippingPostalCode(address.getPostalCode());
        order.setShippingCountry(address.getCountry().trim().toUpperCase());
    }

    private void publishOrderCreatedEvent(Order saved) {
        List<OrderItemEvent> eventItems = saved.getItems().stream()
                .map(item -> new OrderItemEvent(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                ))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                eventItems,
                saved.getId().toString(),
                null
        );

        orderEventPublisher.publishOrderCreated(event);
    }

    private void validateStockAvailability(CreateOrderRequest request) {
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            var inventory = inventoryGrpcClient.getInventory(itemRequest.getProductId());

            if (inventory.getAvailableStock() < itemRequest.getQuantity()) {
                throw new BadRequestException(
                        "Insufficient stock for product: " + itemRequest.getProductId()
                );
            }
        }
    }

    private void reserveStock(List<OrderItem> items, List<OrderItem> reservedItems) {
        for (OrderItem item : items) {
            // Add before the remote call: the Inventory service may commit while this client times
            // out, and the catch block must still be able to compensate that reservation id.
            reservedItems.add(item);
            inventoryGrpcClient.reserveStock(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getInventoryReservationId()
            );
        }
    }

    private void releaseReservedStock(List<OrderItem> reservedItems) {
        for (OrderItem item : reservedItems) {
            try {
                inventoryGrpcClient.releaseStock(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getInventoryReservationId()
                );
            } catch (RuntimeException exception) {
                log.error("Could not compensate inventory reservation after order creation failed. reservationId={}, productId={}",
                        item.getInventoryReservationId(), item.getProductId(), exception);
            }
        }
    }

    private void validateStatusTransition(
            OrderStatus currentStatus,
            OrderStatus targetStatus
    ) {
        if (currentStatus == targetStatus) {
            return;
        }

        switch (currentStatus) {
            case PENDING -> {
                if (targetStatus != OrderStatus.CONFIRMED
                        && targetStatus != OrderStatus.CANCELLED) {
                    throw new BadRequestException(
                            "Invalid transition from PENDING to " + targetStatus
                    );
                }
            }
            case CONFIRMED -> {
                if (targetStatus != OrderStatus.CANCELLED) {
                    throw new BadRequestException(
                            "Invalid transition from CONFIRMED to " + targetStatus
                    );
                }
            }
            case PAYMENT_FAILED -> throw new BadRequestException(
                    "Payment failed orders cannot change state"
            );
            case CANCELLED -> throw new BadRequestException(
                    "Cancelled orders cannot change state"
            );
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getPaymentId(),
                order.getPaymentConfirmedAt(),
                order.getPaymentFailedAt(),
                order.getPaymentFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                toShippingAddressResponse(order),
                items
        );
    }

    private ShippingAddressResponse toShippingAddressResponse(Order order) {
        return new ShippingAddressResponse(
                order.getShippingAddressId(),
                order.getShippingRecipientName(),
                order.getShippingPhone(),
                order.getShippingLine1(),
                order.getShippingLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingPostalCode(),
                order.getShippingCountry()
        );
    }
}
