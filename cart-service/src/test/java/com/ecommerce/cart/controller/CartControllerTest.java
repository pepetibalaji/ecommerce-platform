package com.ecommerce.cart.controller;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.service.CartService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService cartService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CartController cartController;

    @Test
    void shouldAddItem() {
        when(authentication.getName()).thenReturn("user-123");

        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId("11111111-1111-1111-1111-111111111111");
        request.setQuantity(2);

        CartResponse response = new CartResponse(
                "user-123",
                List.of(new CartItemResponse(
                        "item-1",
                        "11111111-1111-1111-1111-111111111111",
                        2
                )),
                LocalDateTime.now()
        );

        when(cartService.addItem(eq("user-123"), any(AddCartItemRequest.class)))
                .thenReturn(response);

        CartResponse result = cartController.addItem(authentication, request);

        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getProductId())
                .isEqualTo("11111111-1111-1111-1111-111111111111");

        verify(cartService).addItem(eq("user-123"), any(AddCartItemRequest.class));
    }

    @Test
    void shouldGetCart() {
        when(authentication.getName()).thenReturn("user-123");

        CartResponse response = new CartResponse(
                "user-123",
                List.of(new CartItemResponse(
                        "item-1",
                        "11111111-1111-1111-1111-111111111111",
                        2
                )),
                LocalDateTime.now()
        );

        when(cartService.getCart("user-123")).thenReturn(response);

        CartResponse result = cartController.getCart(authentication);

        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getItemId()).isEqualTo("item-1");

        verify(cartService).getCart("user-123");
    }

    @Test
    void shouldUpdateItem() {
        when(authentication.getName()).thenReturn("user-123");

        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(5);

        CartResponse response = new CartResponse(
                "user-123",
                List.of(new CartItemResponse(
                        "item-1",
                        "11111111-1111-1111-1111-111111111111",
                        5
                )),
                LocalDateTime.now()
        );

        when(cartService.updateItem(eq("user-123"), eq("item-1"), any(UpdateCartItemRequest.class)))
                .thenReturn(response);

        CartResponse result = cartController.updateItem(authentication, "item-1", request);

        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);

        verify(cartService).updateItem(eq("user-123"), eq("item-1"), any(UpdateCartItemRequest.class));
    }

    @Test
    void shouldRemoveItem() {
        when(authentication.getName()).thenReturn("user-123");

        CartResponse response = new CartResponse(
                "user-123",
                List.of(),
                LocalDateTime.now()
        );

        when(cartService.removeItem("user-123", "item-1")).thenReturn(response);

        CartResponse result = cartController.removeItem(authentication, "item-1");

        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getItems()).isEmpty();

        verify(cartService).removeItem("user-123", "item-1");
    }

    @Test
    void shouldClearCart() {
        when(authentication.getName()).thenReturn("user-123");

        cartController.clearCart(authentication);

        verify(cartService).clearCart("user-123");
    }
}