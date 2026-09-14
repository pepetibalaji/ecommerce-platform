package com.ecommerce.payment.dto.response;

import com.ecommerce.payment.exception.PaymentErrorCode;

public record PaymentApiError(PaymentErrorCode code, String message, boolean retryable,
                              Integer retryAfterSeconds, String traceId) {}
