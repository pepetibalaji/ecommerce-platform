package com.ecommerce.inventory.grpc;

import com.ecommerce.common.exception.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

@GrpcAdvice
public class InventoryGrpcExceptionHandler {

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public StatusRuntimeException handleNotFound(ResourceNotFoundException exception) {
        return Status.NOT_FOUND.withDescription(exception.getMessage()).asRuntimeException();
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public StatusRuntimeException handleInvalidRequest(IllegalArgumentException exception) {
        return Status.FAILED_PRECONDITION
                .withDescription(exception.getMessage())
                .asRuntimeException();
    }
}
