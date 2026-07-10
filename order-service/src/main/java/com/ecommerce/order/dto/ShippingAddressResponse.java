package com.ecommerce.order.dto;

import java.util.UUID;

public record ShippingAddressResponse(
        UUID addressId,
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {
}