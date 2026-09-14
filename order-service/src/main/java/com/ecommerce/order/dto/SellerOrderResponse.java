package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerOrderResponse(
        UUID id,
        OrderStatus status,
        Instant createdAt,
        ShippingAddressResponse shippingAddress,
        String currency,
        BigDecimal sellerTotalAmount,
        List<OrderItemResponse> items
) { }
