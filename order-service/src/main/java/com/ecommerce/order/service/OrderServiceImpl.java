package com.ecommerce.order.service;

import com.ecommerce.common.events.order.OrderCreatedEvent;
import com.ecommerce.common.events.order.OrderCompletedEvent;
import com.ecommerce.common.events.order.OrderItemEvent;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ecommerce.common.events.payment.PaymentFailedEvent;
import com.ecommerce.common.events.payment.PaymentExpiredEvent;
import com.ecommerce.common.events.payment.PaymentRefundFailedEvent;
import com.ecommerce.common.events.payment.PaymentSuccessEvent;
import com.ecommerce.common.events.payment.PaymentRefundCompletedEvent;
import com.ecommerce.common.events.payment.PaymentRefundRequestRejectedEvent;
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
import com.ecommerce.order.catalog.ProductSellerClient;
import com.ecommerce.order.config.CheckoutProperties;
import com.ecommerce.order.dto.SellerOrderResponse;
import com.ecommerce.order.api.OrderApiException;
import com.ecommerce.order.idempotency.OrderIdempotencyRecord;
import com.ecommerce.order.idempotency.OrderIdempotencyService;
import com.ecommerce.common.grpc.exception.GrpcClientException;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ProductSellerClient productSellerClient;
    private final CheckoutProperties checkoutProperties;
    private final Clock clock;
    private final OrderCreatedOutboxService orderCreatedOutboxService;
    private final CheckoutCompensationService checkoutCompensationService;
    private final OrderIdempotencyService orderIdempotencyService;
    private final PlatformTransactionManager transactionManager;
    private final OrderRefundRequestService orderRefundRequestService;
    private final OrderLifecycleAuditService lifecycleAuditService;

    @Value("${order.default-currency:INR}")
    private String defaultCurrency;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            InventoryGrpcClient inventoryGrpcClient,
            OrderEventPublisher orderEventPublisher,
            OrderProcessedEventRepository orderProcessedEventRepository,
            PaymentOutcomeMetrics paymentOutcomeMetrics,
            InventoryReleaseOutboxService inventoryReleaseOutboxService,
            ProductSellerClient productSellerClient,
            CheckoutProperties checkoutProperties,
            Clock clock,
            OrderCreatedOutboxService orderCreatedOutboxService,
            CheckoutCompensationService checkoutCompensationService,
            OrderIdempotencyService orderIdempotencyService,
            PlatformTransactionManager transactionManager,
            OrderRefundRequestService orderRefundRequestService,
            OrderLifecycleAuditService lifecycleAuditService
    ) {
        this.orderRepository = orderRepository;
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderEventPublisher = orderEventPublisher;
        this.orderProcessedEventRepository = orderProcessedEventRepository;
        this.paymentOutcomeMetrics = paymentOutcomeMetrics;
        this.inventoryReleaseOutboxService = inventoryReleaseOutboxService;
        this.productSellerClient = productSellerClient;
        this.checkoutProperties = checkoutProperties;
        this.clock = clock;
        this.orderCreatedOutboxService = orderCreatedOutboxService;
        this.checkoutCompensationService = checkoutCompensationService;
        this.orderIdempotencyService = orderIdempotencyService;
        this.transactionManager = transactionManager;
        this.orderRefundRequestService = orderRefundRequestService;
        this.lifecycleAuditService = lifecycleAuditService;
    }

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        // Compatibility for legacy internal callers. HTTP checkout always uses the keyed overload.
        validateCreateOrderRequest(request);
        return createOrderInTransaction(userId, request, null, null, null);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request, String idempotencyKey) {
        validateCreateOrderRequest(request);

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedIdempotencyKey == null) {
            throw new OrderApiException("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key is required.", false, List.of());
        }
        String requestHash = requestHash(request);

        // The short claim commits before any remote call. Every same-key request subsequently
        // locks it in the checkout transaction, so only one node can reserve inventory.
        orderIdempotencyService.claim(
                userId,
                normalizedIdempotencyKey,
                requestHash,
                checkoutProperties.getIdempotencyRetention());

        try {
            return checkoutTransaction().execute(status -> {
                OrderIdempotencyRecord record = orderIdempotencyService.lock(
                        userId, normalizedIdempotencyKey, requestHash);

                if (record.getOrderId() != null) {
                    return responseForCompletedIdempotencyRecord(record);
                }

                // Supports records produced immediately before this dedicated claim table was
                // introduced, while still serializing all new requests before reservation.
                var legacyOrder = orderRepository.findByUserIdAndIdempotencyKey(userId, normalizedIdempotencyKey);
                if (legacyOrder.isPresent()) {
                    ensureSameIdempotencyRequest(legacyOrder.get(), requestHash);
                    record.complete(legacyOrder.get().getId());
                    return toResponse(legacyOrder.get());
                }

                return createOrderInTransaction(userId, request, normalizedIdempotencyKey, requestHash, record);
            });
        } catch (RuntimeException exception) {
            // saveAndFlush can still hit the legacy unique index during a rolling deployment.
            // This happens after the transaction is rolled back; reload the winner in a fresh
            // transaction and return it instead of surfacing a spurious 500 to a safe retry.
            OrderResponse recovered = recoverOriginalOrder(userId, normalizedIdempotencyKey, requestHash);
            if (recovered != null) {
                return recovered;
            }

            try {
                orderIdempotencyService.releaseIfIncomplete(userId, normalizedIdempotencyKey, requestHash);
            } catch (RuntimeException cleanupFailure) {
                log.error("Could not release incomplete idempotency claim. userId={}, idempotencyKey={}",
                        userId, normalizedIdempotencyKey, cleanupFailure);
            }
            throw exception;
        }
    }

    private TransactionTemplate checkoutTransaction() {
        return new TransactionTemplate(transactionManager);
    }

    private OrderResponse responseForCompletedIdempotencyRecord(OrderIdempotencyRecord record) {
        Order order = orderRepository.findById(record.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Completed idempotency record references a missing order"));
        return toResponse(order);
    }

    private OrderResponse recoverOriginalOrder(UUID userId, String idempotencyKey, String requestHash) {
        try {
            return checkoutTransaction().execute(status -> {
                var existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
                if (existing.isEmpty()) {
                    return null;
                }
                ensureSameIdempotencyRequest(existing.get(), requestHash);
                OrderIdempotencyRecord record = orderIdempotencyService.lock(userId, idempotencyKey, requestHash);
                record.complete(existing.get().getId());
                return toResponse(existing.get());
            });
        } catch (DataIntegrityViolationException ignored) {
            // The original exception remains more useful if the recovery query itself races with
            // an unrelated rolling-deployment constraint change.
            return null;
        }
    }

    private void ensureSameIdempotencyRequest(Order existing, String requestHash) {
        if (!requestHash.equals(existing.getIdempotencyRequestHash())) {
            throw new OrderApiException("IDEMPOTENCY_KEY_REUSED", HttpStatus.CONFLICT,
                    "Idempotency-Key was already used with a different checkout request.", false, List.of());
        }
    }

    private OrderResponse createOrderInTransaction(
            UUID userId,
            CreateOrderRequest request,
            String normalizedIdempotencyKey,
            String requestHash,
            OrderIdempotencyRecord idempotencyRecord
    ) {

        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            Order order = new Order();
            order.setUserId(userId);
            order.setIdempotencyKey(normalizedIdempotencyKey);
            order.setIdempotencyRequestHash(requestHash);
            Clock effectiveClock = clock == null ? Clock.systemUTC() : clock;
            order.setIdempotencyExpiresAt(Instant.now(effectiveClock).plus(checkoutProperties.getIdempotencyRetention()));
            order.setPaymentExpiresAt(Instant.now(effectiveClock).plus(checkoutProperties.getPendingPaymentExpiry()));
            order.setCurrency(resolveCurrency(request.getCurrency()));
            order.setStatus(OrderStatus.PENDING);

            applyShippingAddress(
                    order,
                    request.getShippingAddress()
            );

            List<CreateOrderItemRequest> aggregatedItems = aggregateAndValidateQuantities(request.getItems());
            BigDecimal totalAmount = BigDecimal.ZERO;

            // Complete catalog validation before inventory is queried or any reservation is made.
            for (CreateOrderItemRequest itemRequest : aggregatedItems) {
                ProductSellerClient.OrderableProduct catalogProduct = productSellerClient
                        .getOrderableProduct(itemRequest.getProductId());
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProductId(itemRequest.getProductId());
                item.setSellerId(catalogProduct.sellerId());
                item.setProductName(catalogProduct.name());
                item.setInventoryReservationId(UUID.randomUUID());
                item.setQuantity(itemRequest.getQuantity());
                item.setPrice(catalogProduct.price());

                order.getItems().add(item);

                totalAmount = totalAmount.add(
                        catalogProduct.price()
                                .multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
                );
            }

            validateStockAvailability(aggregatedItems);
            reserveStock(order.getItems(), reservedItems);

            order.setTotalAmount(totalAmount);

            Order saved = normalizedIdempotencyKey == null
                    ? orderRepository.save(order)
                    : orderRepository.saveAndFlush(order);

            if (normalizedIdempotencyKey == null) {
                // Deprecated internal path only; public checkout always takes the durable path.
                publishOrderCreatedEvent(saved);
            } else {
                idempotencyRecord.complete(saved.getId());
                orderCreatedOutboxService.enqueue(saved);
            }

            return toResponse(saved);
        } catch (RuntimeException ex) {
            // A reservation can commit even if the client call times out. Persist compensation in
            // its own transaction before the checkout transaction rolls back, then attempt release.
            if (!reservedItems.isEmpty()) checkoutCompensationService.enqueue(reservedItems);
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
                .orElseThrow(() -> orderNotFound(orderId));

        if (!order.getUserId().equals(userId)) {
            throw orderNotFound(orderId);
        }

        return toResponse(order);
    }

    @Override
    public OrderResponse cancelOrder(UUID userId, UUID orderId) {
        return cancelOrder(userId, orderId, null);
    }

    @Override
    public OrderResponse cancelOrder(UUID userId, UUID orderId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> orderNotFound(orderId));

        if (!order.getUserId().equals(userId)) {
            throw orderNotFound(orderId);
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            orderRefundRequestService.enqueueCancellation(order, userId, "CUSTOMER", reason, false);
            order.setStatus(OrderStatus.CANCELLATION_REQUESTED);
            return toResponse(orderRepository.save(order));
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            requestFullRefund(order, userId, "CUSTOMER", reason);
            return toResponse(orderRepository.save(order));
        }

        if (order.getStatus() == OrderStatus.REFUND_REQUESTED || order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            // A retry after a browser timeout must not enqueue another provider refund.
            return toResponse(order);
        }

        throw cancellationNotAllowed(order);
    }

    @Override
    public OrderResponse requestRefund(UUID adminId, UUID orderId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> orderNotFound(orderId));

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            requestFullRefund(order, adminId, "ADMIN", reason);
            return toResponse(orderRepository.save(order));
        }
        if (order.getStatus() == OrderStatus.REFUND_REQUESTED) {
            return toResponse(order);
        }
        throw new OrderApiException("ORDER_STATE_CONFLICT", HttpStatus.CONFLICT,
                "A refund can only be requested for a confirmed order.", false, List.of());
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

        if (request.getStatus() != order.getStatus()) {
            throw new OrderApiException("ORDER_STATE_CONFLICT", HttpStatus.CONFLICT,
                    "Payment lifecycle changes must use the cancellation or refund workflow.", false, List.of());
        }
        validateStatusTransition(order.getStatus(), request.getStatus());

        if (request.getStatus() == OrderStatus.CANCELLED) {
            inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.CANCELLED);
        }

        order.setStatus(request.getStatus());

        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SellerOrderResponse> getSellerOrders(UUID sellerId, Pageable pageable) {
        return orderRepository.findBySellerId(sellerId, pageable)
                .map(order -> toSellerResponse(order, sellerId));
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

        validateOutcomeTotals(order, event.getUserId(), event.getAmount(), event.getCurrency(), event.getPaymentId());
        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            // Payment owns the already-durable cancellation command and will refund a success race.
            order.setPaymentId(event.getPaymentId());
            order.setPaymentConfirmedAt(now());
            order.setStatus(OrderStatus.REFUND_REQUESTED);
            orderRepository.save(order);
        } else if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentId(event.getPaymentId());
            order.setPaymentConfirmedAt(now());
            order.setPaymentFailedAt(null);
            order.setPaymentFailureReason(null);
            orderRepository.save(order);
            OrderCompletedEvent completedEvent = new OrderCompletedEvent(order.getId(), order.getUserId(),
                    event.getPaymentId(), order.getTotalAmount(), order.getItems().stream()
                    .map(item -> new OrderItemEvent(item.getProductId(), item.getQuantity(), item.getPrice(), item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))).toList(),
                    event.getCorrelationId(), event.getTraceId());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { orderEventPublisher.publishOrderCompleted(completedEvent); }
            });
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

        validateOutcomeTotals(order, event.getUserId(), event.getAmount(), event.getCurrency(), event.getPaymentId());
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            boolean cancelled = "PAYMENT_CANCELLED".equals(event.getFailureCode());
            inventoryReleaseOutboxService.enqueueFor(order, cancelled ? InventoryReleaseReason.CANCELLED : InventoryReleaseReason.PAYMENT_FAILED);
            order.setStatus(cancelled ? OrderStatus.CANCELLED : OrderStatus.PAYMENT_FAILED);
            order.setPaymentId(event.getPaymentId());
            order.setPaymentFailedAt(now());
            order.setPaymentFailureReason("Payment was not completed.");
            orderRepository.save(order);
            paymentOutcomeMetrics.orderUpdated("failure");
        } else if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            log.warn("Ignoring late payment-failed event. eventId={}, orderId={}, paymentId={}, orderStatus={}",
                    event.getEventId(), event.getOrderId(), event.getPaymentId(), order.getStatus());
            paymentOutcomeMetrics.lateEventIgnored("failure");
        }

        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    @Override
    public void handleRefundCompleted(PaymentRefundCompletedEvent event) {
        validatePaymentEvent(event == null ? null : event.getEventId(), event == null ? null : event.getOrderId(),
                event == null ? null : event.getPaymentId());
        Order order = orderRepository.findByIdForUpdate(event.getOrderId()).orElseThrow(() ->
                new ResourceNotFoundException("Order not found for refund event: " + event.getOrderId()));
        if (orderProcessedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Ignoring duplicate payment-refund-completed event. eventId={}, orderId={}, refundId={}",
                    event.getEventId(), event.getOrderId(), event.getRefundId());
            paymentOutcomeMetrics.duplicateIgnored();
            return;
        }
        validateOutcomeTotals(order, event.getUserId(), event.getPaymentAmount(), event.getCurrency(), event.getPaymentId());
        if (event.getAmount() == null || event.getAmount().signum() <= 0 || event.getTotalRefundedAmount() == null
                || event.getAmount().compareTo(event.getTotalRefundedAmount()) > 0
                || event.getTotalRefundedAmount().compareTo(event.getPaymentAmount()) > 0
                || event.isFullRefund() != (event.getTotalRefundedAmount().compareTo(event.getPaymentAmount()) == 0)) {
            throw new BadRequestException("Inconsistent refund outcome totals");
        }
        OrderStatus statusBeforeOutcome = order.getStatus();
        if (order.getStatus() == OrderStatus.REFUNDED) {
            // A reconstructed event may have a different event ID; it must never downgrade a full refund.
            recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
            return;
        }
        if (!event.isFullRefund() && order.getStatus() == OrderStatus.PENDING) {
            // Separate Kafka topics may deliver a partial refund before its original success.
            // Retry after the success consumer has established the paid/fulfilment lifecycle.
            throw new IllegalStateException("Partial refund is awaiting the original payment success outcome");
        }
        order.setPaymentId(event.getPaymentId());
        if (!event.isFullRefund()) {
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                order.setStatus(OrderStatus.PARTIALLY_REFUNDED);
                orderRepository.save(order);
                audit(order.getId(), "REFUND_PARTIALLY_COMPLETED", null,
                        "PAYMENT_SYSTEM", null, event.getRefundId());
            } else if (order.getStatus() == OrderStatus.REFUND_REQUESTED || order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
                // A cancellation requests the complete payment amount. A partial outcome cannot
                // release the reservation and is surfaced for an operations decision.
                order.setStatus(OrderStatus.REFUND_REQUIRES_FULFILMENT_REVIEW);
                orderRepository.save(order);
                audit(order.getId(), "REFUND_PARTIAL_OUTCOME_REQUIRES_REVIEW", null,
                        "PAYMENT_SYSTEM", null, event.getRefundId());
            }
        } else if (order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.PENDING
                || order.getStatus() == OrderStatus.REFUND_FAILED
                || order.getStatus() == OrderStatus.REFUND_REQUESTED
                || order.getStatus() == OrderStatus.CANCELLATION_REQUESTED
                || order.getStatus() == OrderStatus.PARTIALLY_REFUNDED) {
            inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.FULL_REFUND);
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
            audit(order.getId(), "REFUND_COMPLETED", null,
                    "PAYMENT_SYSTEM", null, event.getRefundId());
        } else {
            // A shipped/deducted order must be handled by fulfilment; it is never compensated here.
            order.setStatus(OrderStatus.REFUND_REQUIRES_FULFILMENT_REVIEW);
            orderRepository.save(order);
            audit(order.getId(), "REFUND_OUTCOME_REQUIRES_FULFILMENT_REVIEW", null,
                    "PAYMENT_SYSTEM", null, event.getRefundId());
            log.warn("Full refund requires fulfilment review; release not queued. orderId={}, orderStatus={}",
                    order.getId(), statusBeforeOutcome);
        }
        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    @Override
    public void handleRefundRequestRejected(PaymentRefundRequestRejectedEvent event) {
        validatePaymentEvent(event == null ? null : event.getEventId(),
                event == null ? null : event.getOrderId(), event == null ? null : event.getPaymentId());
        Order order = orderRepository.findByIdForUpdate(event.getOrderId()).orElseThrow(() ->
                new ResourceNotFoundException("Order not found for refund rejection event: " + event.getOrderId()));
        if (orderProcessedEventRepository.existsByEventId(event.getEventId())) {
            paymentOutcomeMetrics.duplicateIgnored();
            return;
        }
        if (order.getStatus() == OrderStatus.REFUND_REQUESTED) {
            order.setStatus(OrderStatus.REFUND_REQUIRES_FULFILMENT_REVIEW);
            orderRepository.save(order);
            audit(order.getId(), "REFUND_REQUEST_REJECTED", null,
                    "PAYMENT_SYSTEM", event.getReason(), event.getRefundRequestId());
            paymentOutcomeMetrics.refundRequestRejected();
        } else {
            log.warn("Ignoring refund-request rejection for order outside refund-request workflow. orderId={}, status={}",
                    order.getId(), order.getStatus());
            paymentOutcomeMetrics.lateEventIgnored("refund_request_rejected");
        }
        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    @Override
    public void handlePaymentExpired(PaymentExpiredEvent event) {
        validatePaymentEvent(event == null ? null : event.getEventId(), event == null ? null : event.getOrderId(),
                event == null ? null : event.getPaymentId());
        var order = orderRepository.findByIdForUpdate(event.getOrderId()).orElseThrow(() -> orderNotFound(event.getOrderId()));
        if (orderProcessedEventRepository.existsByEventId(event.getEventId())) {
            paymentOutcomeMetrics.duplicateIgnored();
            return;
        }
        validateOutcomeTotals(order, event.getUserId(), event.getAmount(), event.getCurrency(), event.getPaymentId());
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            order.setStatus(OrderStatus.PAYMENT_EXPIRED);
            order.setPaymentId(event.getPaymentId());
            order.setPaymentFailedAt(now());
            order.setPaymentFailureReason("Payment time expired.");
            inventoryReleaseOutboxService.enqueueFor(order, InventoryReleaseReason.PAYMENT_EXPIRED);
            orderRepository.save(order);
            paymentOutcomeMetrics.pendingPaymentExpired();
            audit(order.getId(), "PAYMENT_EXPIRED", null, "PAYMENT_SYSTEM", null, null);
        } else {
            paymentOutcomeMetrics.lateEventIgnored("expiry");
        }
        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    @Override
    public void handleRefundFailed(PaymentRefundFailedEvent event) {
        validatePaymentEvent(event == null ? null : event.getEventId(), event == null ? null : event.getOrderId(),
                event == null ? null : event.getPaymentId());
        var order = orderRepository.findByIdForUpdate(event.getOrderId()).orElseThrow(() -> orderNotFound(event.getOrderId()));
        if (orderProcessedEventRepository.existsByEventId(event.getEventId())) {
            paymentOutcomeMetrics.duplicateIgnored();
            return;
        }
        if (!order.getUserId().equals(event.getUserId()) || (order.getPaymentId() != null && !order.getPaymentId().equals(event.getPaymentId()))) {
            throw new BadRequestException("Refund outcome ownership mismatch");
        }
        if (order.getStatus() == OrderStatus.REFUND_REQUESTED || order.getStatus() == OrderStatus.CANCELLATION_REQUESTED
                || order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PARTIALLY_REFUNDED) {
            order.setStatus(OrderStatus.REFUND_FAILED);
            order.setPaymentId(event.getPaymentId());
            orderRepository.save(order);
            audit(order.getId(), "REFUND_FAILED", null, "PAYMENT_SYSTEM", "Refund requires operations review", event.getRefundRequestId());
            paymentOutcomeMetrics.refundRequestRejected();
        }
        recordProcessedEvent(event.getEventId(), event.getEventType(), event.getOrderId());
    }

    private void validateOutcomeTotals(Order order, UUID userId, BigDecimal amount, String currency, UUID paymentId) {
        if (!order.getUserId().equals(userId) || amount == null || order.getTotalAmount().compareTo(amount) != 0
                || !order.getCurrency().equals(currency)
                || (order.getPaymentId() != null && !order.getPaymentId().equals(paymentId))) {
            throw new BadRequestException("Payment outcome does not match immutable order data");
        }
    }

    private void requestFullRefund(Order order, UUID actorId, String actorType, String reason) {
        if (order.getPaymentId() == null) {
            throw new OrderApiException("ORDER_STATE_CONFLICT", HttpStatus.CONFLICT,
                    "A confirmed order cannot be cancelled until its payment reference is available.", false, List.of());
        }
        orderRefundRequestService.enqueueFullRefund(order, actorId, actorType, reason);
        order.setStatus(OrderStatus.REFUND_REQUESTED);
    }

    private OrderApiException cancellationNotAllowed(Order order) {
        return new OrderApiException("ORDER_CANCELLATION_NOT_ALLOWED", HttpStatus.CONFLICT,
                "This order cannot be cancelled in its current lifecycle state.", false,
                List.of(Map.of("orderId", order.getId(), "status", order.getStatus().name())));
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
            throw checkoutError("CHECKOUT_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                    "An order request is required.", false, Map.of());
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw checkoutError("CHECKOUT_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                    "Your order must contain at least one item.", false, Map.of());
        }

        if (request.getShippingAddress() == null) {
            throw checkoutError("CHECKOUT_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                    "A shipping address is required.", false, Map.of());
        }

        for (CreateOrderItemRequest item : request.getItems()) {
            if (item == null) {
                throw checkoutError("CHECKOUT_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                        "Order items must not be null.", false, Map.of());
            }
            if (item.getProductId() == null) {
                throw checkoutError("CHECKOUT_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                        "Each order item needs a product ID.", false, Map.of());
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw checkoutError("CHECKOUT_ITEM_INVALID_QUANTITY", HttpStatus.BAD_REQUEST,
                        "Each order item needs a positive quantity.", false,
                        Map.of("productId", item.getProductId()));
            }

        }
    }

    private String resolveCurrency(String currency) {
        String resolved = (currency == null || currency.isBlank())
                ? defaultCurrency
                : currency;

        resolved = resolved.trim().toUpperCase();

        if (!resolved.matches("^[A-Z]{3}$")) {
            throw checkoutError("CHECKOUT_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                    "Currency must be a three-letter ISO code, for example INR or USD.", false, Map.of());
        }

        return resolved;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new OrderApiException("IDEMPOTENCY_KEY_INVALID", HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed 100 characters.", false, List.of());
        }
        return normalized;
    }

    private void applyShippingAddress(
            Order order,
            ShippingAddressRequest address
    ) {
        order.setShippingRecipientName(address.getRecipientName());
        order.setShippingPhone(address.getPhone());
        order.setShippingLine1(address.getLine1());
        order.setShippingLine2(address.getLine2());
        order.setShippingCity(address.getCity());
        order.setShippingState(address.getState());
        order.setShippingPostalCode(address.getPostalCode());
        order.setShippingCountry(address.getCountry().trim().toUpperCase());
    }

    private String requestHash(CreateOrderRequest request) {
        String address = request.getShippingAddress().getRecipientName() + "|" + request.getShippingAddress().getPhone()
                + "|" + request.getShippingAddress().getLine1() + "|" + request.getShippingAddress().getLine2()
                + "|" + request.getShippingAddress().getCity() + "|" + request.getShippingAddress().getState()
                + "|" + request.getShippingAddress().getPostalCode() + "|" + request.getShippingAddress().getCountry();
        String items = request.getItems().stream().sorted(java.util.Comparator.comparing(CreateOrderItemRequest::getProductId))
                .map(i -> i.getProductId() + ":" + i.getQuantity()).collect(java.util.stream.Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((resolveCurrency(request.getCurrency()) + "|" + address + "|" + items).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
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

    private List<CreateOrderItemRequest> aggregateAndValidateQuantities(List<CreateOrderItemRequest> requestedItems) {
        Map<UUID, Integer> quantitiesByProduct = new LinkedHashMap<>();
        int totalQuantity = 0;

        for (CreateOrderItemRequest item : requestedItems) {
            try {
                totalQuantity = Math.addExact(totalQuantity, item.getQuantity());
                quantitiesByProduct.merge(item.getProductId(), item.getQuantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw checkoutError("CHECKOUT_ITEM_INVALID_QUANTITY", HttpStatus.BAD_REQUEST,
                        "An item quantity is too large.", false, Map.of("productId", item.getProductId()));
            }
        }
        if (totalQuantity > checkoutProperties.getMaxTotalQuantity()) {
            throw checkoutError("CHECKOUT_ORDER_QUANTITY_LIMIT", HttpStatus.BAD_REQUEST,
                    "Your order exceeds the allowed total quantity.", false,
                    Map.of("requestedQuantity", totalQuantity,
                            "maximumQuantity", checkoutProperties.getMaxTotalQuantity()));
        }

        List<CreateOrderItemRequest> aggregatedItems = new ArrayList<>();
        quantitiesByProduct.forEach((productId, quantity) -> {
            int maximum = checkoutProperties.maximumQuantityFor(productId);
            if (maximum <= 0 || quantity > maximum) {
                throw checkoutError("CHECKOUT_ITEM_QUANTITY_LIMIT", HttpStatus.BAD_REQUEST,
                        "One or more items exceed the allowed quantity.", false,
                        Map.of("productId", productId, "requestedQuantity", quantity,
                                "maximumQuantity", maximum));
            }
            CreateOrderItemRequest aggregated = new CreateOrderItemRequest();
            aggregated.setProductId(productId);
            aggregated.setQuantity(quantity);
            aggregatedItems.add(aggregated);
        });
        return aggregatedItems;
    }

    private void validateStockAvailability(List<CreateOrderItemRequest> items) {
        for (CreateOrderItemRequest itemRequest : items) {
            var inventory = inventoryForCheckout(itemRequest.getProductId());

            if (inventory.getAvailableStock() < itemRequest.getQuantity()) {
                throw checkoutError("CHECKOUT_ITEM_INSUFFICIENT_STOCK", HttpStatus.CONFLICT,
                        "One or more items are no longer available in the requested quantity.", false,
                        Map.of("productId", itemRequest.getProductId(),
                                "requestedQuantity", itemRequest.getQuantity(),
                                "availableQuantity", inventory.getAvailableStock()));
            }
        }
    }

    private void reserveStock(List<OrderItem> items, List<OrderItem> reservedItems) {
        for (OrderItem item : items) {
            // Add before the remote call: the Inventory service may commit while this client times
            // out, and the catch block must still be able to compensate that reservation id.
            reservedItems.add(item);
            try {
                inventoryGrpcClient.reserveStock(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getInventoryReservationId()
                );
            } catch (GrpcClientException exception) {
                throw inventoryUnavailable(item.getProductId());
            } catch (BadRequestException exception) {
                // Availability can change after the pre-flight read. Inventory intentionally does
                // not leak its internal error text through the browser-facing checkout contract.
                throw checkoutError("CHECKOUT_ITEM_INSUFFICIENT_STOCK", HttpStatus.CONFLICT,
                        "One or more items are no longer available in the requested quantity.", false,
                        Map.of("productId", item.getProductId(),
                                "requestedQuantity", item.getQuantity()));
            }
        }
    }

    private com.ecommerce.proto.inventory.InventoryDetails inventoryForCheckout(UUID productId) {
        try {
            return inventoryGrpcClient.getInventory(productId);
        } catch (GrpcClientException exception) {
            throw inventoryUnavailable(productId);
        }
    }

    private OrderApiException inventoryUnavailable(UUID productId) {
        return checkoutError("CHECKOUT_INVENTORY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Inventory is temporarily unavailable. Please retry this checkout.", true,
                Map.of("productId", productId));
    }

    private OrderApiException orderNotFound(UUID orderId) {
        return new OrderApiException("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Order not found.", false, List.of(Map.of("orderId", orderId)));
    }

    private OrderApiException checkoutError(
            String code,
            HttpStatus status,
            String message,
            boolean retryable,
            Map<String, ?> details
    ) {
        return new OrderApiException(code, status, message, retryable,
                details.isEmpty() ? List.of() : List.of(details));
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
            case CANCELLATION_REQUESTED, REFUND_REQUESTED, REFUND_FAILED, PARTIALLY_REFUNDED, REFUNDED, REFUND_REQUIRES_FULFILMENT_REVIEW -> throw new BadRequestException(
                    "Refunded orders require fulfilment/manual reconciliation before state changes");
            case PAYMENT_FAILED, PAYMENT_EXPIRED -> throw new BadRequestException(
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
                        item.getProductName(), item.getQuantity(), item.getPrice(),
                        item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                ))
                .toList();

        boolean cancellationAllowed = order.getStatus() == OrderStatus.PENDING
                || order.getStatus() == OrderStatus.CONFIRMED;
        String cancellationReasonCode = cancellationAllowed
                ? null
                : order.getStatus() == OrderStatus.REFUND_REQUESTED
                        ? "REFUND_IN_PROGRESS"
                        : "ORDER_CANCELLATION_NOT_ALLOWED";

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
                cancellationAllowed,
                cancellationReasonCode,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                toShippingAddressResponse(order),
                items
        );
    }

    private ShippingAddressResponse toShippingAddressResponse(Order order) {
        return new ShippingAddressResponse(
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

    private SellerOrderResponse toSellerResponse(Order order, UUID sellerId) {
        List<OrderItemResponse> items = order.getItems().stream()
                .filter(item -> sellerId.equals(item.getSellerId()))
                .map(item -> new OrderItemResponse(item.getId(), item.getProductId(), item.getProductName(), item.getQuantity(), item.getPrice(), item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();
        BigDecimal sellerTotal = items.stream()
                .map(OrderItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SellerOrderResponse(order.getId(), order.getStatus(), order.getCreatedAt(),
                toShippingAddressResponse(order), order.getCurrency(), sellerTotal, items);
    }

    private Instant now() {
        return Instant.now(clock == null ? Clock.systemUTC() : clock);
    }

    /** Allows legacy isolated unit tests to omit the optional audit collaborator. */
    private void audit(UUID orderId, String action, UUID actorId, String actorType, String reason, UUID refundRequestId) {
        if (lifecycleAuditService != null) {
            lifecycleAuditService.record(orderId, action, actorId, actorType, reason, refundRequestId);
        }
    }
}
