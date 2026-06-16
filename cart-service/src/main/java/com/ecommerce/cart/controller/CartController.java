package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "Redis-backed shopping cart APIs")
public class CartController {

    private static final String USER_ID_CLAIM = "userId";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    @Operation(summary = "Add item to cart")
    public CartResponse addItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Valid
            @RequestBody AddCartItemRequest request
    ) {
        String userId = getUserId(jwt);

        return cartService.addItem(userId, request);
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update cart item quantity")
    public CartResponse updateItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "Cart item ID")
            @PathVariable String itemId,

            @Valid
            @RequestBody UpdateCartItemRequest request
    ) {
        String userId = getUserId(jwt);

        return cartService.updateItem(userId, itemId, request);
    }

    @GetMapping
    @Operation(summary = "Get current user's cart")
    public CartResponse getCart(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = getUserId(jwt);

        return cartService.getCart(userId);
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove item from cart")
    public CartResponse removeItem(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = "Cart item ID")
            @PathVariable String itemId
    ) {
        String userId = getUserId(jwt);

        return cartService.removeItem(userId, itemId);
    }

    @DeleteMapping
    @Operation(summary = "Clear current user's cart")
    public void clearCart(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = getUserId(jwt);

        cartService.clearCart(userId);
    }

    private String getUserId(Jwt jwt) {
        return jwt.getClaimAsString(USER_ID_CLAIM);
    }
}