package com.ecommerce.cart.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Cart implements Serializable {

    private String userId;
    private List<CartItem> items = new ArrayList<>();
    @JsonDeserialize(using = UtcInstantDeserializer.class)
    private Instant updatedAt;
    private long version;

    public Cart(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
        this.updatedAt = Instant.now();
    }
}
