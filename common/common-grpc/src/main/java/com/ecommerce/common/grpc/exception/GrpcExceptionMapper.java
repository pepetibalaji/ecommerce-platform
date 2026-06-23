package com.ecommerce.common.grpc.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public final class GrpcExceptionMapper {

    private GrpcExceptionMapper() {
    }

    public static GrpcClientException map(
            String serviceName,
            StatusRuntimeException exception
    ) {
        Status status = exception.getStatus();
        Status.Code code = status.getCode();

        String message = switch (code) {
            case DEADLINE_EXCEEDED ->
                    serviceName + " gRPC call timed out";
            case UNAVAILABLE ->
                    serviceName + " gRPC service is unavailable";
            case NOT_FOUND ->
                    serviceName + " gRPC resource was not found";
            case INVALID_ARGUMENT ->
                    serviceName + " gRPC request was invalid";
            case FAILED_PRECONDITION ->
                    serviceName + " gRPC request failed precondition";
            case PERMISSION_DENIED ->
                    serviceName + " gRPC request was denied";
            case UNAUTHENTICATED ->
                    serviceName + " gRPC request was unauthenticated";
            case CANCELLED ->
                    serviceName + " gRPC call was cancelled";
            default ->
                    serviceName + " gRPC call failed with status: " + code;
        };

        String description = status.getDescription();

        if (description != null && !description.isBlank()) {
            message = message + " - " + description;
        }

        return new GrpcClientException(
                serviceName,
                code,
                message,
                exception
        );
    }
}