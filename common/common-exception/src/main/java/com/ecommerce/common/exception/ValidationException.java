package com.ecommerce.common.exception;

public class ValidationException
        extends RuntimeException {

    public ValidationException(String message) {

        super(message);
    }
}