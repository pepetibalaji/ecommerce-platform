package com.ecommerce.cart.repository;

import java.time.Duration;

import com.ecommerce.cart.model.Cart;
import com.ecommerce.common.redis.key.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CartRedisRepository {

    private static final Duration CART_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CartRedisRepository(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(Cart cart) {
        String key = RedisKeys.cart(cart.getUserId());
        redisTemplate.opsForValue().set(key, cart, CART_TTL);
    }

    public Cart findByUserId(String userId) {
        Object value = redisTemplate.opsForValue().get(RedisKeys.cart(userId));

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
}