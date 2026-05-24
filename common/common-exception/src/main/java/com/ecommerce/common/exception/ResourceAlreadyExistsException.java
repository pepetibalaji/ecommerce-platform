package com.ecommerce.common.exception;

public class ResourceAlreadyExistsException
        extends RuntimeException {

    public ResourceAlreadyExistsException(String message) {

        super(message);
    }
}