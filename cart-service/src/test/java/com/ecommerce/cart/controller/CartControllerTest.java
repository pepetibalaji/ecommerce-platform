package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.service.CartService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private static final String USER_ID =
            "11111111-1111-1111-1111-111111111111";

    private static final String PRODUCT_ID =
            "22222222-2222-2222-2222-222222222222";

    private static final String ITEM_ID =
            "item-1";

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    @Test
    void shouldAddItem() {

        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(PRODUCT_ID);
        request.setQuantity(2);

        CartResponse response = new CartResponse(
                USER_ID,
                List.of(
                        new CartItemResponse(
                                ITEM_ID,
                                PRODUCT_ID,
                                2
                        )
                ),
                LocalDateTime.now()
        );

        when(
                cartService.addItem(
                        eq(USER_ID),
                        any(AddCartItemRequest.class)
                )
        ).thenReturn(response);

        CartResponse result =
                cartController.addItem(
                        jwt(),
                        request
                );

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getItems())
                .hasSize(1);

        assertThat(result.getItems().get(0).getProductId())
                .isEqualTo(PRODUCT_ID);

        verify(cartService)
                .addItem(
                        eq(USER_ID),
                        any(AddCartItemRequest.class)
                );
    }

    @Test
    void shouldGetCart() {

        CartResponse response = new CartResponse(
                USER_ID,
                List.of(
                        new CartItemResponse(
                                ITEM_ID,
                                PRODUCT_ID,
                                2
                        )
                ),
                LocalDateTime.now()
        );

        when(cartService.getCart(USER_ID))
                .thenReturn(response);

        CartResponse result =
                cartController.getCart(jwt());

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getItems())
                .hasSize(1);

        assertThat(result.getItems().get(0).getItemId())
                .isEqualTo(ITEM_ID);

        verify(cartService)
                .getCart(USER_ID);
    }

    @Test
    void shouldUpdateItem() {

        UpdateCartItemRequest request =
                new UpdateCartItemRequest();

        request.setQuantity(5);

        CartResponse response = new CartResponse(
                USER_ID,
                List.of(
                        new CartItemResponse(
                                ITEM_ID,
                                PRODUCT_ID,
                                5
                        )
                ),
                LocalDateTime.now()
        );

        when(
                cartService.updateItem(
                        eq(USER_ID),
                        eq(ITEM_ID),
                        any(UpdateCartItemRequest.class)
                )
        ).thenReturn(response);

        CartResponse result =
                cartController.updateItem(
                        jwt(),
                        ITEM_ID,
                        request
                );

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getItems())
                .hasSize(1);

        assertThat(result.getItems().get(0).getQuantity())
                .isEqualTo(5);

        verify(cartService)
                .updateItem(
                        eq(USER_ID),
                        eq(ITEM_ID),
                        any(UpdateCartItemRequest.class)
                );
    }

    @Test
    void shouldRemoveItem() {

        CartResponse response = new CartResponse(
                USER_ID,
                List.of(),
                LocalDateTime.now()
        );

        when(cartService.removeItem(USER_ID, ITEM_ID))
                .thenReturn(response);

        CartResponse result =
                cartController.removeItem(
                        jwt(),
                        ITEM_ID
                );

        assertThat(result.getUserId())
                .isEqualTo(USER_ID);

        assertThat(result.getItems())
                .isEmpty();

        verify(cartService)
                .removeItem(USER_ID, ITEM_ID);
    }

    @Test
    void shouldClearCart() {

        cartController.clearCart(jwt());

        verify(cartService)
                .clearCart(USER_ID);
    }

    private Jwt jwt() {

        Instant now = Instant.now();

        return Jwt.withTokenValue("test-access-token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("customer@example.com")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("userId", USER_ID)
                .claim("role", "CUSTOMER")
                .claim("tokenVersion", 0)
                .build();
    }
}