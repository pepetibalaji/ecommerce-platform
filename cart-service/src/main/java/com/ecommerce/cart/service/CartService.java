package com.ecommerce.cart.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.util.function.Supplier;

import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.repository.CartRedisRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.redis.key.RedisKeys;
import com.ecommerce.common.redis.lock.DistributedLockService;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    private final CartRedisRepository cartRedisRepository;
    private final DistributedLockService distributedLockService;

    public CartService(CartRedisRepository cartRedisRepository,
                       DistributedLockService distributedLockService) {
        this.cartRedisRepository = cartRedisRepository;
        this.distributedLockService = distributedLockService;
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

    public CartResponse createGuestCart(String guestId) {
        return withGuestLock(guestId, () -> toResponse(getOrCreateGuestCart(guestId)));
    }

    public CartResponse getGuestCart(String guestId) {
        return withGuestLock(guestId, () -> toResponse(getOrCreateGuestCart(guestId)));
    }

    public CartResponse addGuestItem(String guestId, AddCartItemRequest request) {
        return withGuestLock(guestId, () -> {
            Cart cart = getOrCreateGuestCart(guestId);
            CartItem existing = cart.getItems().stream()
                    .filter(item -> item.getProductId().equals(request.getProductId()))
                    .findFirst().orElse(null);
            if (existing == null) {
                cart.getItems().add(new CartItem(UUID.randomUUID().toString(), request.getProductId(), request.getQuantity()));
            } else {
                existing.setQuantity(existing.getQuantity() + request.getQuantity());
            }
            cart.setUpdatedAt(LocalDateTime.now());
            cartRedisRepository.saveGuestCart(guestId, cart);
            return toResponse(cart);
        });
    }

    public CartResponse updateGuestItem(String guestId, String itemId, UpdateCartItemRequest request) {
        return withGuestLock(guestId, () -> {
            Cart cart = getExistingGuestCart(guestId);
            CartItem item = cart.getItems().stream().filter(i -> i.getItemId().equals(itemId)).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Guest cart item not found: " + itemId));
            item.setQuantity(request.getQuantity());
            cart.setUpdatedAt(LocalDateTime.now());
            cartRedisRepository.saveGuestCart(guestId, cart);
            return toResponse(cart);
        });
    }

    public CartResponse removeGuestItem(String guestId, String itemId) {
        return withGuestLock(guestId, () -> {
            Cart cart = getExistingGuestCart(guestId);
            if (!cart.getItems().removeIf(item -> item.getItemId().equals(itemId))) {
                throw new ResourceNotFoundException("Guest cart item not found: " + itemId);
            }
            cart.setUpdatedAt(LocalDateTime.now());
            if (cart.getItems().isEmpty()) {
                cartRedisRepository.deleteByGuestId(guestId);
                return new CartResponse(guestId, List.of(), cart.getUpdatedAt());
            }
            cartRedisRepository.saveGuestCart(guestId, cart);
            return toResponse(cart);
        });
    }

    public void clearGuestCart(String guestId) {
        withGuestLock(guestId, () -> {
            cartRedisRepository.deleteByGuestId(guestId);
            return null;
        });
    }

    public CartResponse mergeGuestCart(String userId, String guestId) {
        return withGuestLock(guestId, () -> withCustomerLock(userId, () -> {
            Cart guestCart = cartRedisRepository.findByGuestId(guestId);
            Cart customerCart = getOrCreateCart(userId);
            if (guestCart == null || guestCart.getItems().isEmpty()) {
                return toResponse(customerCart);
            }
            for (CartItem guestItem : guestCart.getItems()) {
                CartItem customerItem = customerCart.getItems().stream()
                        .filter(item -> item.getProductId().equals(guestItem.getProductId()))
                        .findFirst().orElse(null);
                if (customerItem == null) {
                    customerCart.getItems().add(new CartItem(UUID.randomUUID().toString(), guestItem.getProductId(), guestItem.getQuantity()));
                } else {
                    customerItem.setQuantity(customerItem.getQuantity() + guestItem.getQuantity());
                }
            }
            customerCart.setUpdatedAt(LocalDateTime.now());
            // Deletion is deliberately after the destination save so retries remain possible.
            cartRedisRepository.save(customerCart);
            cartRedisRepository.deleteByGuestId(guestId);
            return toResponse(customerCart);
        }));
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

    private Cart getOrCreateGuestCart(String guestId) {
        Cart cart = cartRedisRepository.findByGuestId(guestId);
        if (cart == null) {
            cart = new Cart(guestId);
            cartRedisRepository.saveGuestCart(guestId, cart);
        }
        return cart;
    }

    private Cart getExistingGuestCart(String guestId) {
        Cart cart = cartRedisRepository.findByGuestId(guestId);
        if (cart == null) {
            throw new ResourceNotFoundException("Guest cart not found");
        }
        return cart;
    }

    private <T> T withGuestLock(String guestId, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        String key = RedisKeys.cartLock("guest", guestId);
        if (!distributedLockService.tryLock(key, token, Duration.ofSeconds(15))) {
            throw new IllegalStateException("Guest cart is busy; retry the request");
        }
        try {
            return action.get();
        } finally {
            distributedLockService.unlock(key, token);
        }
    }

    private <T> T withCustomerLock(String userId, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        String key = RedisKeys.cartLock("customer", userId);
        if (!distributedLockService.tryLock(key, token, Duration.ofSeconds(15))) {
            throw new IllegalStateException("Customer cart is busy; retry the request");
        }
        try {
            return action.get();
        } finally {
            distributedLockService.unlock(key, token);
        }
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(item.getItemId(), item.getProductId(), item.getQuantity()))
                .toList();

        return new CartResponse(cart.getUserId(), items, cart.getUpdatedAt());
    }
}
