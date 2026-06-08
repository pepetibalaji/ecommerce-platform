package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRedisRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRedisRepository cartRedisRepository;

    @InjectMocks
    private CartService cartService;

    private final String userId = "user-123";
    private final String productId = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        reset(cartRedisRepository);
    }

    @Test
    void addItem_shouldCreateCartWhenMissing() {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(productId);
        request.setQuantity(2);

        when(cartRedisRepository.findByUserId(userId)).thenReturn(null);

        CartResponse response = cartService.addItem(userId, request);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(productId);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);

        verify(cartRedisRepository, times(2)).save(any(Cart.class));
    }

    @Test
    void addItem_shouldIncreaseQuantityForExistingProduct() {
        Cart cart = new Cart(userId);
        cart.getItems().add(new CartItem("item-1", productId, 2));

        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(productId);
        request.setQuantity(3);

        when(cartRedisRepository.findByUserId(userId)).thenReturn(cart);

        CartResponse response = cartService.addItem(userId, request);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemId()).isEqualTo("item-1");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(5);

        verify(cartRedisRepository).save(any(Cart.class));
    }

    @Test
    void updateItem_shouldUpdateQuantity() {
        Cart cart = new Cart(userId);
        cart.getItems().add(new CartItem("item-1", productId, 2));

        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(7);

        when(cartRedisRepository.findByUserId(userId)).thenReturn(cart);

        CartResponse response = cartService.updateItem(userId, "item-1", request);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemId()).isEqualTo("item-1");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(7);

        verify(cartRedisRepository).save(any(Cart.class));
    }

    @Test
    void getCart_shouldReturnExistingCart() {
        Cart cart = new Cart(userId);
        cart.getItems().add(new CartItem("item-1", productId, 2));

        when(cartRedisRepository.findByUserId(userId)).thenReturn(cart);

        CartResponse response = cartService.getCart(userId);

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getItemId()).isEqualTo("item-1");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void removeItem_shouldDeleteCartWhenLastItemRemoved() {
        Cart cart = new Cart(userId);
        cart.getItems().add(new CartItem("item-1", productId, 2));

        when(cartRedisRepository.findByUserId(userId)).thenReturn(cart);

        CartResponse response = cartService.removeItem(userId, "item-1");

        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getItems()).isEmpty();

        verify(cartRedisRepository).deleteByUserId(userId);
    }

    @Test
    void removeItem_shouldThrowWhenItemMissing() {
        Cart cart = new Cart(userId);
        cart.getItems().add(new CartItem("item-1", productId, 2));

        when(cartRedisRepository.findByUserId(userId)).thenReturn(cart);

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.removeItem(userId, "missing-item"));
    }

    @Test
    void clearCart_shouldDeleteCart() {
        cartService.clearCart(userId);

        verify(cartRedisRepository).deleteByUserId(userId);
    }
}