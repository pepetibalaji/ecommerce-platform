package com.ecommerce.common.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;

import java.util.Map;

@RestControllerAdvice

public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExists(
            ResourceAlreadyExistsException exception,
            HttpServletRequest request
    ) {

        ApiErrorResponse response =
                ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error("Conflict")
                        .message(exception.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {

        ApiErrorResponse response =
                ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("Not Found")
                        .message(exception.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            UnauthorizedException exception,
            HttpServletRequest request
    ) {

        ApiErrorResponse response =
                ApiErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .error("Unauthorized")
                        .message(exception.getMessage())
                        .path(request.getRequestURI())
                        .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception exception,
            HttpServletRequest request
    ) {

            ApiErrorResponse response = ApiErrorResponse.builder()
                            .timestamp(LocalDateTime.now())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .error("Internal Server Error")
                            .message(exception.getMessage())
                            .path(request.getRequestURI())
                            .build();

            return ResponseEntity.status(
                            HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(response);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<?> handleValidation(
        MethodArgumentNotValidException exception
) {

    Map<String, String> errors = new HashMap<>();

    exception.getBindingResult()
            .getFieldErrors()
            .forEach(error ->
                    errors.put(
                            error.getField(),
                            error.getDefaultMessage()
                    )
            );

    return ResponseEntity.badRequest()
            .body(errors);
}
}