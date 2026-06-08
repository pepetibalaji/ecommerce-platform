package com.ecommerce.cart.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Cart implements Serializable {

    private String userId;
    private List<CartItem> items = new ArrayList<>();
    private LocalDateTime updatedAt;

    public Cart(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
        this.updatedAt = LocalDateTime.now();
    }
}