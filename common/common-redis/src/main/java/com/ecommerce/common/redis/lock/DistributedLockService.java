package com.ecommerce.common.redis.lock;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DistributedLockService {

    private final StringRedisTemplate stringRedisTemplate;

    public DistributedLockService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryLock(String key, String value, Duration ttl) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(success);
    }

    public void unlock(String key, String expectedValue) {
        String currentValue = stringRedisTemplate.opsForValue().get(key);
        if (expectedValue != null && expectedValue.equals(currentValue)) {
            stringRedisTemplate.delete(key);
        }
    }

    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
}