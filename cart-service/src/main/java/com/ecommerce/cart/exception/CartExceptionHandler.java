package com.ecommerce.cart.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.dao.DataAccessResourceFailureException;

@RestControllerAdvice(basePackages = "com.ecommerce.cart")
public class CartExceptionHandler {
    @ExceptionHandler(CartBusyException.class)
    ResponseEntity<Map<String, Object>> handleBusy(CartBusyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(Map.of("timestamp", Instant.now().toString(), "status", 409,
                        "code", "CART_LOCK_CONTENTION", "message", exception.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("timestamp", Instant.now().toString(), "status", 409,
                        "code", "IDEMPOTENCY_KEY_CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler({RedisConnectionFailureException.class, DataAccessResourceFailureException.class})
    ResponseEntity<Map<String, Object>> handleRedisUnavailable(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(Map.of("timestamp", Instant.now().toString(), "status", 503,
                        "code", "CART_REDIS_UNAVAILABLE", "message", "Cart storage is temporarily unavailable; retry shortly."));
    }
}
