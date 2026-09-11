package com.ecommerce.product.exception;

import com.ecommerce.common.exception.ApiErrorResponse;
import com.ecommerce.product.controller.ProductController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Keeps catalogue dependency and request failures out of the shared generic-500 handler. */
@RestControllerAdvice(basePackageClasses = ProductController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductExceptionHandler {
  @ExceptionHandler(SellerEligibilityUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> eligibilityUnavailable(HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE,
        "Seller eligibility is temporarily unavailable. Please try again later.", request);
  }

  @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class,
      TransientDataAccessException.class, TransactionSystemException.class})
  public ResponseEntity<ApiErrorResponse> catalogueUnavailable(HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE,
        "The catalogue is temporarily unavailable. Please try again later.", request);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiErrorResponse> concurrentUpdate(HttpServletRequest request) {
    return error(HttpStatus.CONFLICT,
        "This product changed during your update. Reload it and try again.", request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> invalidParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return validationError(Map.of(exception.getName(), "Invalid value"), request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiErrorResponse> missingParameter(
      MissingServletRequestParameterException exception, HttpServletRequest request) {
    return validationError(Map.of(exception.getParameterName(), "This field is required"), request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> invalidConstraints(
      ConstraintViolationException exception, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception.getConstraintViolations().forEach(violation ->
        fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
    return validationError(fields, request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiErrorResponse> invalidMethodArguments(
      HandlerMethodValidationException exception, HttpServletRequest request) {
    if (exception.isForReturnValue()) {
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }
    Map<String, String> fields = new LinkedHashMap<>();
    exception.getParameterValidationResults().forEach(result -> {
      String parameter = Objects.toString(result.getMethodParameter().getParameterName(), "request");
      String field = parameter + (result.getContainerIndex() == null ? "" : "[" + result.getContainerIndex() + "]");
      if (result instanceof ParameterErrors errors) {
        errors.getFieldErrors().forEach(error -> fields.putIfAbsent(field + "." + error.getField(),
            Objects.toString(error.getDefaultMessage(), "Invalid value")));
        errors.getGlobalErrors().forEach(error -> fields.putIfAbsent(field,
            Objects.toString(error.getDefaultMessage(), "Invalid value")));
      } else {
        result.getResolvableErrors().forEach(error -> fields.putIfAbsent(field,
            Objects.toString(error.getDefaultMessage(), "Invalid value")));
      }
    });
    return validationError(fields, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> invalidRequest(HttpServletRequest request) {
    return validationError(Map.of("body", "Request body contains missing or invalid fields"), request);
  }

  private ResponseEntity<ApiErrorResponse> validationError(Map<String, String> fields, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "Request validation failed", request, fields);
  }

  private ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String message, HttpServletRequest request) {
    return error(status, message, request, Map.of());
  }

  private ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String message, HttpServletRequest request, Map<String, String> fields) {
    return ResponseEntity.status(status).body(ApiErrorResponse.builder()
        .timestamp(LocalDateTime.now(java.time.Clock.systemUTC()))
        .status(status.value())
        .error(status.getReasonPhrase())
        .message(message)
        .path(request.getRequestURI())
        .fieldErrors(Map.copyOf(fields))
        .build());
  }
}
