package com.ecommerce.cart.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRedisRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    private final CartRedisRepository cartRedisRepository;

    public CartService(CartRedisRepository cartRedisRepository) {
        this.cartRedisRepository = cartRedisRepository;
    }

    public CartResponse addItem(String userId, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(userId);

        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
        } else {
            cart.getItems().add(new CartItem(
                    UUID.randomUUID().toString(),
                    request.getProductId(),
                    request.getQuantity()
            ));
        }

        cart.setUpdatedAt(LocalDateTime.now());
        cartRedisRepository.save(cart);

        return toResponse(cart);
    }

    public CartResponse updateItem(String userId, String itemId, UpdateCartItemRequest request) {
        Cart cart = getExistingCart(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

        item.setQuantity(request.getQuantity());
        cart.setUpdatedAt(LocalDateTime.now());
        cartRedisRepository.save(cart);

        return toResponse(cart);
    }

    public CartResponse getCart(String userId) {
        return toResponse(getOrCreateCart(userId));
    }

    public CartResponse removeItem(String userId, String itemId) {
        Cart cart = getExistingCart(userId);

        boolean removed = cart.getItems().removeIf(item -> item.getItemId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found: " + itemId);
        }

        cart.setUpdatedAt(LocalDateTime.now());

        if (cart.getItems().isEmpty()) {
            cartRedisRepository.deleteByUserId(userId);
            return new CartResponse(userId, List.of(), cart.getUpdatedAt());
        }

        cartRedisRepository.save(cart);
        return toResponse(cart);
    }

    public void clearCart(String userId) {
        cartRedisRepository.deleteByUserId(userId);
    }

    private Cart getOrCreateCart(String userId) {
        Cart cart = cartRedisRepository.findByUserId(userId);
        if (cart == null) {
            cart = new Cart(userId);
            cart.setItems(new ArrayList<>());
            cart.setUpdatedAt(LocalDateTime.now());
            cartRedisRepository.save(cart);
        }
        return cart;
    }

    private Cart getExistingCart(String userId) {
        Cart cart = cartRedisRepository.findByUserId(userId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart not found for user: " + userId);
        }
        return cart;
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(item.getItemId(), item.getProductId(), item.getQuantity()))
                .toList();

        return new CartResponse(cart.getUserId(), items, cart.getUpdatedAt());
    }
}