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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "Redis-backed shopping cart APIs")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    @Operation(summary = "Add item to cart")
    public CartResponse addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(authentication.getName(), request);
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Update cart item quantity")
    public CartResponse updateItem(
            Authentication authentication,
            @Parameter(description = "Cart item ID") @PathVariable String itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(authentication.getName(), itemId, request);
    }

    @GetMapping
    @Operation(summary = "Get current user's cart")
    public CartResponse getCart(Authentication authentication) {
        return cartService.getCart(authentication.getName());
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove item from cart")
    public CartResponse removeItem(
            Authentication authentication,
            @Parameter(description = "Cart item ID") @PathVariable String itemId
    ) {
        return cartService.removeItem(authentication.getName(), itemId);
    }

    @DeleteMapping
    @Operation(summary = "Clear current user's cart")
    public void clearCart(Authentication authentication) {
        cartService.clearCart(authentication.getName());
    }
}