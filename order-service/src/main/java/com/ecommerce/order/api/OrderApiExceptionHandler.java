package com.ecommerce.order.api;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.ecommerce.order")
public class OrderApiExceptionHandler {
    @ExceptionHandler(OrderApiException.class)
    ResponseEntity<Map<String, Object>> handle(OrderApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "code", ex.getCode(), "message", ex.getMessage(), "retryable", ex.isRetryable(),
                "details", ex.getDetails(), "traceId", java.util.Optional.ofNullable(MDC.get("traceId")).orElse("")));
    }
}
