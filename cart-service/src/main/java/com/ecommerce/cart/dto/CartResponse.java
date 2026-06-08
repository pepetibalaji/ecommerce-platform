package com.ecommerce.cart.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String userId;
    private List<CartItemResponse> items;
    private LocalDateTime updatedAt;
}