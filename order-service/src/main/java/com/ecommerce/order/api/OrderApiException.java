package com.ecommerce.order.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.List;

@Getter
public class OrderApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final boolean retryable;
    private final List<?> details;
    public OrderApiException(String code, HttpStatus status, String message, boolean retryable, List<?> details) {
        super(message); this.code = code; this.status = status; this.retryable = retryable; this.details = details == null ? List.of() : List.copyOf(details);
    }
}
