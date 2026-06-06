package com.ecommerce.common.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RedisConnectionTest.TestApplication.class)
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class RedisConnectionTest {

    @SpringBootApplication
    static class TestApplication {
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void shouldConnectToRedis() {
        redisTemplate.opsForValue().set("redis-test", "working");

        String value = redisTemplate.opsForValue().get("redis-test");

        assertThat(value).isEqualTo("working");
    }
}