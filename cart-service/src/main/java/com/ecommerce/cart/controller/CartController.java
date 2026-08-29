package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.MergeGuestCartRequest;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.config.CartProperties;
import com.ecommerce.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "Redis-backed shopping cart APIs")
public class CartController {

    private static final String USER_ID_CLAIM = "userId";

    private final CartService cartService;
    private final CartProperties cartProperties;

    public CartController(CartService cartService, CartProperties cartProperties) {
        this.cartService = cartService;
        this.cartProperties = cartProperties;
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

    @PostMapping("/guest")
    @Operation(summary = "Create an anonymous guest cart")
    public CartResponse createGuestCart(@CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                                        HttpServletResponse response) {
        String guestId = guestId(cookieGuestId, response);
        return cartService.createGuestCart(guestId);
    }

    @GetMapping("/guest")
    @Operation(summary = "Get the anonymous guest cart")
    public CartResponse getGuestCart(@CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                                     HttpServletResponse response) {
        String guestId = guestId(cookieGuestId, response);
        return cartService.getGuestCart(guestId);
    }

    @PostMapping("/guest/items")
    @Operation(summary = "Add an item to the anonymous guest cart")
    public CartResponse addGuestItem(@CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                                     HttpServletResponse response, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addGuestItem(guestId(cookieGuestId, response), request);
    }

    @PutMapping("/guest/items/{itemId}")
    @Operation(summary = "Update an anonymous guest-cart item")
    public CartResponse updateGuestItem(@CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                                        HttpServletResponse response, @PathVariable String itemId,
                                        @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateGuestItem(guestId(cookieGuestId, response), itemId, request);
    }

    @DeleteMapping("/guest/items/{itemId}")
    @Operation(summary = "Remove an item from the anonymous guest cart")
    public CartResponse removeGuestItem(@CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                                        HttpServletResponse response, @PathVariable String itemId) {
        return cartService.removeGuestItem(guestId(cookieGuestId, response), itemId);
    }

    @DeleteMapping("/guest")
    @Operation(summary = "Clear the anonymous guest cart")
    public void clearGuestCart(@CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                               HttpServletResponse response) {
        cartService.clearGuestCart(guestId(cookieGuestId, response));
    }

    @PostMapping("/merge-guest")
    @Operation(summary = "Merge the current guest cart into the authenticated customer cart")
    public CartResponse mergeGuestCart(@AuthenticationPrincipal Jwt jwt,
                                       @CookieValue(value = "${cart.guest.cookie.name:guestId}", required = false) String cookieGuestId,
                                       @RequestBody(required = false) MergeGuestCartRequest request) {
        String requestedGuestId = request == null ? null : request.getGuestId();
        if (cookieGuestId != null && requestedGuestId != null && !cookieGuestId.equals(requestedGuestId)) {
            throw new IllegalArgumentException("guestId does not match the guest cookie");
        }
        String guestId = cookieGuestId != null ? cookieGuestId : requestedGuestId;
        if (guestId == null || !isUuid(guestId)) {
            throw new IllegalArgumentException("A valid guest cart identity is required");
        }
        return cartService.mergeGuestCart(getUserId(jwt), guestId);
    }

    private String getUserId(Jwt jwt) {
        return jwt.getClaimAsString(USER_ID_CLAIM);
    }

    private String guestId(String cookieGuestId, HttpServletResponse response) {
        String guestId = isUuid(cookieGuestId) ? cookieGuestId : UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(cartProperties.getGuest().getCookie().getName(), guestId)
                .httpOnly(true).secure(cartProperties.getGuest().getCookie().isSecure())
                .sameSite(cartProperties.getGuest().getCookie().getSameSite())
                .path("/api/v1/cart").maxAge(cartProperties.getGuest().getTtl()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return guestId;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
