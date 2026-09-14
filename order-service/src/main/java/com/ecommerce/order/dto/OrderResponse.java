package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;

    private UUID userId;

    private BigDecimal totalAmount;

    private String currency;

    private OrderStatus status;

    private UUID paymentId;

    private Instant paymentConfirmedAt;

    private Instant paymentFailedAt;

    private String paymentFailureReason;

    private boolean cancelAllowed;

    private String cancellationReasonCode;

    private Instant createdAt;

    private Instant updatedAt;

    private ShippingAddressResponse shippingAddress;

    private List<OrderItemResponse> items;

    public OrderResponse(
            UUID id,
            UUID userId,
            BigDecimal totalAmount,
            String currency,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            ShippingAddressResponse shippingAddress,
            List<OrderItemResponse> items
    ) {
        this(id, userId, totalAmount, currency, status, null, null, null, null, false, null,
                createdAt, updatedAt, shippingAddress, items);
    }
}
