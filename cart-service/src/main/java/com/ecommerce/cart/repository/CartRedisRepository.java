package com.ecommerce.cart.repository;

import com.ecommerce.cart.config.CartProperties;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.cart.model.IdempotencyRecord;
import java.time.Duration;
import com.ecommerce.common.redis.key.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

@Repository
public class CartRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CartProperties cartProperties;

    public CartRedisRepository(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            CartProperties cartProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cartProperties = cartProperties;
    }

    public void save(Cart cart) {
        String key = RedisKeys.cart(cart.getUserId());
        redisTemplate.opsForValue().set(key, cart, cartProperties.getCustomer().getTtl());
    }

    public Cart findByUserId(String userId) {
        return find(RedisKeys.cart(userId));
    }

    public void saveGuestCart(String guestId, Cart cart) {
        redisTemplate.opsForValue().set(RedisKeys.guestCart(guestId), cart, cartProperties.getGuest().getTtl());
    }

    public Cart findByGuestId(String guestId) {
        return find(RedisKeys.guestCart(guestId));
    }

    public void deleteByGuestId(String guestId) {
        redisTemplate.delete(RedisKeys.guestCart(guestId));
    }

    private Cart find(String key) {
        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof Cart cart) {
            return cart;
        }

        return objectMapper.convertValue(value, Cart.class);
    }

    public void deleteByUserId(String userId) {
        redisTemplate.delete(RedisKeys.cart(userId));
    }

    public boolean existsByUserId(String userId) {
        Boolean exists = redisTemplate.hasKey(RedisKeys.cart(userId));
        return Boolean.TRUE.equals(exists);
    }

    public IdempotencyRecord findIdempotencyRecord(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : objectMapper.convertValue(value, IdempotencyRecord.class);
    }

    public void saveIdempotencyRecord(String key, IdempotencyRecord record, Duration ttl) {
        redisTemplate.opsForValue().set(key, record, ttl);
    }

    /** Redis MULTI/EXEC makes the destination save, source deletion, and replay record one atomic commit. */
    public void mergeAtomically(Cart customerCart, String guestId, String idempotencyKey,
                                IdempotencyRecord record, Duration idempotencyTtl) {
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                operations.multi();
                operations.opsForValue().set(RedisKeys.cart(customerCart.getUserId()), customerCart, cartProperties.getCustomer().getTtl());
                operations.delete(RedisKeys.guestCart(guestId));
                operations.opsForValue().set(idempotencyKey, record, idempotencyTtl);
                return operations.exec();
            }
        });
    }
}
