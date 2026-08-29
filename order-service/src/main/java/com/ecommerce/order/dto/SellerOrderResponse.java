package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SellerOrderResponse(
        UUID id,
        OrderStatus status,
        LocalDateTime createdAt,
        ShippingAddressResponse shippingAddress,
        BigDecimal sellerTotalAmount,
        List<OrderItemResponse> items
) { }
