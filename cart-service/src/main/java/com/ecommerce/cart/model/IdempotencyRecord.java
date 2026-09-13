package com.ecommerce.cart.model;

import java.io.Serializable;
import com.ecommerce.cart.dto.CartResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord implements Serializable {
    private String fingerprint;
    private CartResponse response;
}
