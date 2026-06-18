package com.ecommerce.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotEmpty
    @Valid
    private List<CreateOrderItemRequest> items;
}