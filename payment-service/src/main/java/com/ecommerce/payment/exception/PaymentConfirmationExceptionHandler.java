package com.ecommerce.payment.exception;

import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceAlreadyExistsException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import com.ecommerce.payment.dto.response.PaymentApiError;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.UUID;

@RestControllerAdvice(basePackages = {"com.ecommerce.payment.controller", "com.ecommerce.payment.outbox"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentConfirmationExceptionHandler {
    @ExceptionHandler(PaymentApiException.class)
    public ResponseEntity<PaymentApiError> payment(PaymentApiException exception) { return error(exception.getCode()); }
    @ExceptionHandler({PaymentConfirmationUnavailableException.class, DataAccessException.class})
    public ResponseEntity<PaymentApiError> unavailable() { return error(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE); }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<PaymentApiError> notFound() { return error(PaymentErrorCode.PAYMENT_NOT_FOUND); }
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<PaymentApiError> forbidden() { return error(PaymentErrorCode.PAYMENT_NOT_OWNED); }
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<PaymentApiError> conflict() { return error(PaymentErrorCode.PAYMENT_STATE_CONFLICT); }
    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class,
            jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<PaymentApiError> invalid() { return error(PaymentErrorCode.PAYMENT_INVALID_REQUEST); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<PaymentApiError> denied() { return error(PaymentErrorCode.PAYMENT_NOT_OWNED); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<PaymentApiError> rejected(org.springframework.web.server.ResponseStatusException exception,
                                                   jakarta.servlet.http.HttpServletRequest request) {
        if (!exception.getStatusCode().is4xxClientError())
            return error(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
        PaymentErrorCode code = switch (exception.getStatusCode().value()) {
            case 400 -> request.getRequestURI().startsWith("/api/v1/payments/webhooks/")
                    ? PaymentErrorCode.PAYMENT_WEBHOOK_INVALID : PaymentErrorCode.PAYMENT_INVALID_REQUEST;
            case 403 -> PaymentErrorCode.PAYMENT_NOT_OWNED;
            case 404 -> PaymentErrorCode.PAYMENT_NOT_FOUND;
            case 409 -> PaymentErrorCode.PAYMENT_STATE_CONFLICT;
            default -> PaymentErrorCode.PAYMENT_INVALID_REQUEST;
        };
        // Keep intentional 4xx status codes; never expose exception reasons or provider text.
        var safe = error(code);
        return ResponseEntity.status(exception.getStatusCode()).headers(safe.getHeaders()).body(safe.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentApiError> unexpected(Exception exception) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("payment_api_unexpected_failure type={}", exception.getClass().getSimpleName());
        return error(PaymentErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
    }
    private ResponseEntity<PaymentApiError> error(PaymentErrorCode code) {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();
        var response = ResponseEntity.status(code.getHttpStatus());
        if (code.getRetryAfterSeconds() != null) response.header("Retry-After", code.getRetryAfterSeconds().toString());
        return response.body(new PaymentApiError(code, code.getMessage(), code.isRetryable(), code.getRetryAfterSeconds(), traceId));
    }
}
