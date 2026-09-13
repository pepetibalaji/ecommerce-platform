package com.ecommerce.cart.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() { super("Idempotency-Key was already used for a different request."); }
}
