package com.ecommerce.common.redis.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (var connection = connectionFactory.getConnection()) {
            String ping = connection.ping();
            if ("PONG".equalsIgnoreCase(ping)) {
                return Health.up().withDetail("redis", "available").build();
            }
            return Health.down().withDetail("redis", "unexpected response: " + ping).build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}