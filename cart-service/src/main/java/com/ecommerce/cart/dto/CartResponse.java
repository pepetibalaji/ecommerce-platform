package com.ecommerce.cart.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String ownerType;
    private String ownerId;
    private List<CartItemResponse> items;
    private Instant updatedAt;
    private long version;

    /** Temporary source compatibility for internal callers; not part of the JSON contract. */
    public CartResponse(String ownerId, List<CartItemResponse> items, Instant updatedAt, long version) {
        this("CUSTOMER", ownerId, items, updatedAt, version);
    }

    @JsonIgnore
    public String getUserId() {
        return ownerId;
    }
}
