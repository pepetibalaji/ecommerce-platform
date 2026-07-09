package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateOrderRequest {

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(
            regexp = "^[A-Za-z]{3}$",
            message = "Currency must be a 3-letter ISO code, for example INR or USD"
    )
    private String currency;

    /*
     * Optional for now.
     * Later, when Address Service exists, frontend can send only addressId,
     * and Order Service can fetch + validate the address through gRPC.
     */
    private UUID shippingAddressId;

    @Valid
    @NotNull(message = "Shipping address is required")
    private ShippingAddressRequest shippingAddress;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<CreateOrderItemRequest> items;
}