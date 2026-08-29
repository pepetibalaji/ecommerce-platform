package com.ecommerce.cart.service;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRedisRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.redis.lock.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRedisRepository cartRedisRepository;

    @Mock
    private DistributedLockService distributedLockService;

    @InjectMocks
    private CartService cartService;

    private final String userId = "user-123";
    private final String productId = "11111111-1111-1111-1111-111111111111";

    @BeforeEach
    void setUp() {
        reset(cartRedisRepository, distributedLockService);
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

    @Test
    void mergeGuestCart_shouldAddQuantitiesSaveCustomerThenDeleteGuest() {
        String guestId = "guest-123";
        Cart guestCart = new Cart(guestId);
        guestCart.getItems().add(new CartItem("guest-item", productId, 2));
        Cart customerCart = new Cart(userId);
        customerCart.getItems().add(new CartItem("customer-item", productId, 3));

        when(distributedLockService.tryLock(anyString(), anyString(), any())).thenReturn(true);
        when(cartRedisRepository.findByGuestId(guestId)).thenReturn(guestCart);
        when(cartRedisRepository.findByUserId(userId)).thenReturn(customerCart);

        CartResponse response = cartService.mergeGuestCart(userId, guestId);

        assertThat(response.getItems()).singleElement().extracting("quantity").isEqualTo(5);
        verify(cartRedisRepository).save(customerCart);
        verify(cartRedisRepository).deleteByGuestId(guestId);
    }

    @Test
    void mergeGuestCart_shouldBeIdempotentAfterGuestCartWasDeleted() {
        String guestId = "guest-123";
        Cart customerCart = new Cart(userId);
        customerCart.getItems().add(new CartItem("customer-item", productId, 5));

        when(distributedLockService.tryLock(anyString(), anyString(), any())).thenReturn(true);
        when(cartRedisRepository.findByGuestId(guestId)).thenReturn(null);
        when(cartRedisRepository.findByUserId(userId)).thenReturn(customerCart);

        CartResponse response = cartService.mergeGuestCart(userId, guestId);

        assertThat(response.getItems()).singleElement().extracting("quantity").isEqualTo(5);
        verify(cartRedisRepository, never()).deleteByGuestId(guestId);
    }
}
