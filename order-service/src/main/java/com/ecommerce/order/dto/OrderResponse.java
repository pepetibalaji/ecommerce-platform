package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    private LocalDateTime paymentConfirmedAt;

    private LocalDateTime paymentFailedAt;

    private String paymentFailureReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private ShippingAddressResponse shippingAddress;

    private List<OrderItemResponse> items;

    public OrderResponse(
            UUID id,
            UUID userId,
            BigDecimal totalAmount,
            String currency,
            OrderStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            ShippingAddressResponse shippingAddress,
            List<OrderItemResponse> items
    ) {
        this(id, userId, totalAmount, currency, status, null, null, null, null,
                createdAt, updatedAt, shippingAddress, items);
    }
}
