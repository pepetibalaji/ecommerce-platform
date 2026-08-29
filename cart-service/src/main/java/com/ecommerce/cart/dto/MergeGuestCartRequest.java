package com.ecommerce.cart.dto;

import lombok.Data;

/**
 * Optional fallback for deployments where the HttpOnly guest cookie cannot be
 * forwarded. When a cookie is present, the controller requires this value to
 * match it.
 */
@Data
public class MergeGuestCartRequest {

    private String guestId;
}
