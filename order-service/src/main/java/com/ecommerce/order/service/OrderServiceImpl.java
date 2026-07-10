package com.ecommerce.order.service;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.order.OrderItemEvent;
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
import com.ecommerce.order.grpc.InventoryGrpcClient;
import com.ecommerce.order.kafka.OrderEventPublisher;
import com.ecommerce.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderEventPublisher orderEventPublisher;

    @Value("${order.default-currency:INR}")
    private String defaultCurrency;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            InventoryGrpcClient inventoryGrpcClient,
            OrderEventPublisher orderEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        validateCreateOrderRequest(request);

        List<CreateOrderItemRequest> reservedItems = new ArrayList<>();

        try {
            validateStockAvailability(request);
            reserveStock(request, reservedItems);

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
                item.setQuantity(itemRequest.getQuantity());
                item.setPrice(itemRequest.getPrice());

                order.getItems().add(item);

                totalAmount = totalAmount.add(
                        itemRequest.getPrice()
                                .multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                );
            }

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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        validateStatusTransition(order.getStatus(), OrderStatus.CANCELLED);

        releaseReservedStock(order);

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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        validateStatusTransition(order.getStatus(), request.getStatus());

        order.setStatus(request.getStatus());

        return toResponse(orderRepository.save(order));
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

    private void reserveStock(
            CreateOrderRequest request,
            List<CreateOrderItemRequest> reservedItems
    ) {
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            inventoryGrpcClient.reserveStock(
                    itemRequest.getProductId(),
                    itemRequest.getQuantity()
            );

            reservedItems.add(itemRequest);
        }
    }

    private void releaseReservedStock(List<CreateOrderItemRequest> reservedItems) {
        for (CreateOrderItemRequest itemRequest : reservedItems) {
            try {
                inventoryGrpcClient.releaseStock(
                        itemRequest.getProductId(),
                        itemRequest.getQuantity()
                );
            } catch (Exception ignored) {
                // best-effort rollback
            }
        }
    }

    private void releaseReservedStock(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                inventoryGrpcClient.releaseStock(
                        item.getProductId(),
                        item.getQuantity()
                );
            } catch (Exception ignored) {
                // best-effort rollback
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