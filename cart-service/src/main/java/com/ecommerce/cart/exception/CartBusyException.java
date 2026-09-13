package com.ecommerce.cart.exception;

public class CartBusyException extends RuntimeException {
    public CartBusyException() {
        super("Cart mutation is in progress; retry this request.");
    }
}
