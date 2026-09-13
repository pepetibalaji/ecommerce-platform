package com.ecommerce.inventory.service;

/** Signals a retryable Product Service dependency failure without leaking transport details. */
public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(Throwable cause) { super(cause); }
}
