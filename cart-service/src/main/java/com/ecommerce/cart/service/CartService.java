package com.ecommerce.cart.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.ecommerce.cart.config.CartProperties;
import com.ecommerce.cart.dto.AddCartItemRequest;
import com.ecommerce.cart.dto.CartItemResponse;
import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.cart.dto.UpdateCartItemRequest;
import com.ecommerce.cart.exception.CartBusyException;
import com.ecommerce.cart.exception.IdempotencyConflictException;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.CartItem;
import com.ecommerce.cart.model.IdempotencyRecord;
import com.ecommerce.cart.repository.CartRedisRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.redis.key.RedisKeys;
import com.ecommerce.common.redis.lock.DistributedLockService;
import com.ecommerce.common.events.order.OrderItemEvent;
import org.springframework.stereotype.Service;

@Service
public class CartService {
    private final CartRedisRepository repository;
    private final DistributedLockService locks;
    private final CartProperties properties;

    public CartService(CartRedisRepository repository, DistributedLockService locks, CartProperties properties) {
        this.repository = repository;
        this.locks = locks;
        this.properties = properties;
    }

    public CartResponse addItem(String userId, AddCartItemRequest request) {
        return addItem(userId, request, null);
    }
    public CartResponse addItem(String userId, AddCartItemRequest request, String idempotencyKey) {
        return customerLocked(userId, () -> idempotent("customer", userId, "add", idempotencyKey,
                request.getProductId() + ":" + request.getQuantity(), () -> { Cart cart = customer(userId, true); add(cart, request.getProductId(), request.getQuantity()); saveCustomer(cart); return response("CUSTOMER", cart); }));
    }
    public CartResponse updateItem(String userId, String itemId, UpdateCartItemRequest request) {
        return customerLocked(userId, () -> { Cart cart = customer(userId, false); item(cart, itemId, "Cart item").setQuantity(request.getQuantity()); saveCustomer(cart); return response("CUSTOMER", cart); });
    }
    /** Reads a committed Redis snapshot without competing with cart mutations. */
    public CartResponse getCart(String userId) {
        Cart cart = repository.findByUserId(userId);
        return response("CUSTOMER", cart == null ? new Cart(userId) : cart);
    }
    public CartResponse removeItem(String userId, String itemId) {
        return customerLocked(userId, () -> removeCustomer(userId, itemId));
    }
    public void clearCart(String userId) { customerLocked(userId, () -> { repository.deleteByUserId(userId); return null; }); }

    public CartResponse createGuestCart(String guestId) { return guestLocked(guestId, () -> response("GUEST", guest(guestId, true))); }
    public CartResponse getGuestCart(String guestId) {
        Cart cart = repository.findByGuestId(guestId);
        return response("GUEST", cart == null ? new Cart(guestId) : cart);
    }
    public CartResponse addGuestItem(String guestId, AddCartItemRequest request) {
        return addGuestItem(guestId, request, null);
    }
    public CartResponse addGuestItem(String guestId, AddCartItemRequest request, String idempotencyKey) {
        return guestLocked(guestId, () -> idempotent("guest", guestId, "add", idempotencyKey,
                request.getProductId() + ":" + request.getQuantity(), () -> { Cart cart = guest(guestId, true); add(cart, request.getProductId(), request.getQuantity()); saveGuest(guestId, cart); return response("GUEST", cart); }));
    }
    public CartResponse updateGuestItem(String guestId, String itemId, UpdateCartItemRequest request) {
        return guestLocked(guestId, () -> { Cart cart = guest(guestId, false); item(cart, itemId, "Guest cart item").setQuantity(request.getQuantity()); saveGuest(guestId, cart); return response("GUEST", cart); });
    }
    public CartResponse removeGuestItem(String guestId, String itemId) {
        return guestLocked(guestId, () -> removeGuest(guestId, itemId));
    }
    public void clearGuestCart(String guestId) { guestLocked(guestId, () -> { repository.deleteByGuestId(guestId); return null; }); }

    /** Removes only quantities purchased by a confirmed order; repeated Kafka deliveries replay safely. */
    public void removePurchasedItems(String userId, UUID eventId, List<OrderItemEvent> purchasedItems) {
        customerLocked(userId, () -> {
            String key = "cart-order-completed:" + eventId;
            if (repository.findIdempotencyRecord(key) != null) return null;
            Cart cart = repository.findByUserId(userId);
            if (cart != null) {
                for (OrderItemEvent purchased : purchasedItems) {
                    cart.getItems().removeIf(line -> {
                        if (!line.getProductId().equals(purchased.getProductId().toString())) return false;
                        int remaining = line.getQuantity() - purchased.getQuantity();
                        if (remaining > 0) { line.setQuantity(remaining); return false; }
                        return true;
                    });
                }
                if (cart.getItems().isEmpty()) repository.deleteByUserId(userId); else saveCustomer(cart);
            }
            repository.saveIdempotencyRecord(key, new IdempotencyRecord("order-completed", null), properties.getCustomer().getTtl());
            return null;
        });
    }

    public CartResponse mergeGuestCart(String userId, String guestId) {
        return mergeGuestCart(userId, guestId, null);
    }
    public CartResponse mergeGuestCart(String userId, String guestId, String idempotencyKey) {
        return guestLocked(guestId, () -> customerLocked(userId, () -> {
            String mergeKey = idempotencyKey == null || idempotencyKey.isBlank() ? "guest-" + guestId : idempotencyKey;
            String storageKey = "cart-idempotency:customer:" + userId + ":merge:" + mergeKey;
            IdempotencyRecord prior = repository.findIdempotencyRecord(storageKey);
            if (prior != null) {
                if (!guestId.equals(prior.getFingerprint())) throw new IdempotencyConflictException();
                return prior.getResponse();
            }
            Cart guest = repository.findByGuestId(guestId);
            Cart customer = customer(userId, true);
            if (guest == null || guest.getItems().isEmpty()) {
                CartResponse result = response("CUSTOMER", customer);
                repository.saveIdempotencyRecord(storageKey, new IdempotencyRecord(guestId, result), properties.getIdempotency().getTtl());
                return result;
            }
            for (CartItem item : guest.getItems()) add(customer, item.getProductId(), item.getQuantity());
            touch(customer);
            CartResponse result = response("CUSTOMER", customer);
            repository.mergeAtomically(customer, guestId, storageKey, new IdempotencyRecord(guestId, result), properties.getIdempotency().getTtl());
            return result;
        }));
    }

    private CartResponse removeCustomer(String userId, String itemId) {
        Cart cart = customer(userId, false);
        if (!cart.getItems().removeIf(i -> i.getItemId().equals(itemId))) throw new ResourceNotFoundException("Cart item not found: " + itemId);
        if (cart.getItems().isEmpty()) { touch(cart); repository.deleteByUserId(userId); } else saveCustomer(cart);
        return response("CUSTOMER", cart);
    }
    private CartResponse removeGuest(String guestId, String itemId) {
        Cart cart = guest(guestId, false);
        if (!cart.getItems().removeIf(i -> i.getItemId().equals(itemId))) throw new ResourceNotFoundException("Guest cart item not found: " + itemId);
        if (cart.getItems().isEmpty()) { touch(cart); repository.deleteByGuestId(guestId); } else saveGuest(guestId, cart);
        return response("GUEST", cart);
    }
    private void add(Cart cart, String productId, int quantity) {
        CartItem existing = cart.getItems().stream().filter(i -> i.getProductId().equals(productId)).findFirst().orElse(null);
        if (existing == null) {
            if (cart.getItems().size() >= properties.getLimits().getMaxLines()) throw new IllegalArgumentException("Cart line limit exceeded");
            validQuantity(quantity);
            cart.getItems().add(new CartItem(UUID.randomUUID().toString(), productId, quantity));
            return;
        }
        long total = (long) existing.getQuantity() + quantity;
        if (total > properties.getLimits().getMaxQuantityPerItem()) throw new IllegalArgumentException("Cart item quantity limit exceeded");
        existing.setQuantity((int) total);
    }
    private void validQuantity(int quantity) { if (quantity < 1 || quantity > properties.getLimits().getMaxQuantityPerItem()) throw new IllegalArgumentException("Cart item quantity limit exceeded"); }
    private CartItem item(Cart cart, String itemId, String label) { return cart.getItems().stream().filter(i -> i.getItemId().equals(itemId)).findFirst().orElseThrow(() -> new ResourceNotFoundException(label + " not found: " + itemId)); }
    private Cart customer(String ownerId, boolean create) { Cart cart = repository.findByUserId(ownerId); if (cart == null && create) { cart = new Cart(ownerId); cart.setItems(new ArrayList<>()); repository.save(cart); } if (cart == null) throw new ResourceNotFoundException("Cart not found for user: " + ownerId); return cart; }
    private Cart guest(String ownerId, boolean create) { Cart cart = repository.findByGuestId(ownerId); if (cart == null && create) { cart = new Cart(ownerId); repository.saveGuestCart(ownerId, cart); } if (cart == null) throw new ResourceNotFoundException("Guest cart not found"); return cart; }
    private void saveCustomer(Cart cart) { touch(cart); repository.save(cart); }
    private void saveGuest(String guestId, Cart cart) { touch(cart); repository.saveGuestCart(guestId, cart); }
    private void touch(Cart cart) { cart.setUpdatedAt(Instant.now()); cart.setVersion(cart.getVersion() + 1); }
    private <T> T guestLocked(String id, Supplier<T> action) { return locked("guest", id, action); }
    private <T> T customerLocked(String id, Supplier<T> action) { return locked("customer", id, action); }
    private <T> T locked(String type, String id, Supplier<T> action) { String token = UUID.randomUUID().toString(); String key = RedisKeys.cartLock(type, id); if (!locks.tryLock(key, token, Duration.ofSeconds(15))) throw new CartBusyException(); try { return action.get(); } finally { locks.unlock(key, token); } }
    private CartResponse idempotent(String ownerType, String ownerId, String operation, String key, String fingerprint, Supplier<CartResponse> action) {
        if (key == null || key.isBlank()) return action.get();
        String storageKey = "cart-idempotency:" + ownerType + ":" + ownerId + ":" + operation + ":" + key;
        IdempotencyRecord existing = repository.findIdempotencyRecord(storageKey);
        if (existing != null) {
            if (!fingerprint.equals(existing.getFingerprint())) throw new IdempotencyConflictException();
            return existing.getResponse();
        }
        CartResponse result = action.get();
        repository.saveIdempotencyRecord(storageKey, new IdempotencyRecord(fingerprint, result), properties.getIdempotency().getTtl());
        return result;
    }
    private CartResponse response(String ownerType, Cart cart) { List<CartItemResponse> items = cart.getItems().stream().map(i -> new CartItemResponse(i.getItemId(), i.getProductId(), i.getQuantity())).toList(); return new CartResponse(ownerType, cart.getUserId(), items, cart.getUpdatedAt(), cart.getVersion()); }
}
