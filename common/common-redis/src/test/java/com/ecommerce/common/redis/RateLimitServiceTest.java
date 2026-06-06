package com.ecommerce.common.redis.ratelimit;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RateLimitServiceTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class RateLimitServiceTest {

    @SpringBootApplication
    static class TestApplication {
    }

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void shouldAllowWithinLimitAndBlockAfterLimit() {
        String key = "rate-limit:test-user";

        rateLimitService.reset(key);

        assertThat(rateLimitService.allowRequest(key, 2, Duration.ofSeconds(30))).isTrue();
        assertThat(rateLimitService.allowRequest(key, 2, Duration.ofSeconds(30))).isTrue();
        assertThat(rateLimitService.allowRequest(key, 2, Duration.ofSeconds(30))).isFalse();
    }
}