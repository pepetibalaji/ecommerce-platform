package com.ecommerce.common.grpc.exception;

import io.grpc.Status;

public class GrpcClientException extends RuntimeException {

    private final String serviceName;
    private final Status.Code statusCode;

    public GrpcClientException(
            String serviceName,
            Status.Code statusCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Status.Code getStatusCode() {
        return statusCode;
    }

    public boolean isTimeout() {
        return Status.Code.DEADLINE_EXCEEDED.equals(statusCode);
    }

    public boolean isUnavailable() {
        return Status.Code.UNAVAILABLE.equals(statusCode);
    }
}