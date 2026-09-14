package com.ecommerce.order.dto;

import java.util.UUID;

public record ShippingAddressResponse(
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {
    /** Source compatibility only; address ids are not retained in the public response. */
    public ShippingAddressResponse(UUID ignoredAddressId, String recipientName, String phone, String line1, String line2,
                                   String city, String state, String postalCode, String country) {
        this(recipientName, phone, line1, line2, city, state, postalCode, country);
    }
}
