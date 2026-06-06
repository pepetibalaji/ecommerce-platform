package com.ecommerce.common.redis.lock;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DistributedLockServiceTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class DistributedLockServiceTest {

    @SpringBootApplication
    static class TestApplication {
    }

    @Autowired
    private DistributedLockService lockService;

    @Test
    void shouldAcquireAndReleaseLock() {
        String key = "inventory-lock:test-product";
        String token = UUID.randomUUID().toString();

        boolean acquired = lockService.tryLock(key, token, Duration.ofSeconds(10));
        assertThat(acquired).isTrue();

        assertThat(lockService.isLocked(key)).isTrue();

        lockService.unlock(key, token);

        assertThat(lockService.isLocked(key)).isFalse();
    }
}