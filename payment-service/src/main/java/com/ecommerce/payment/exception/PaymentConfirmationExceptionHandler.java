package com.ecommerce.payment.exception;

@org.springframework.web.bind.annotation.RestControllerAdvice(basePackages = "com.ecommerce.payment.controller")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class PaymentConfirmationExceptionHandler {
    @org.springframework.web.bind.annotation.ExceptionHandler({PaymentConfirmationUnavailableException.class,
            org.springframework.dao.OptimisticLockingFailureException.class, org.springframework.dao.PessimisticLockingFailureException.class})
    public org.springframework.http.ResponseEntity<com.ecommerce.common.exception.ApiErrorResponse> unavailable(jakarta.servlet.http.HttpServletRequest request) {
        return org.springframework.http.ResponseEntity.status(503).body(com.ecommerce.common.exception.ApiErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now(java.time.Clock.systemUTC())).status(503).error("Service Unavailable")
                .message("Payment confirmation is temporarily unavailable. Do not pay again; retry the status check.")
                .path(request.getRequestURI()).build());
    }
}
