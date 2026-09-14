package com.ecommerce.payment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode {
    PAYMENT_PREPARING(404, "Payment is being prepared. Please wait a moment.", true, 2),
    PAYMENT_NOT_FOUND(404, "Payment was not found.", false, null),
    PAYMENT_NOT_OWNED(403, "You cannot access this payment.", false, null),
    PAYMENT_CHECKOUT_ALREADY_ACTIVE(409, "A checkout session is already active.", true, 2),
    PAYMENT_ALREADY_COMPLETED(409, "Payment has already completed.", false, null),
    PAYMENT_PROCESSING(409, "Payment is processing. Please check its status shortly.", true, 2),
    PAYMENT_EXPIRED(409, "Payment has expired. Please create a new order.", false, null),
    PAYMENT_CANCELLED(409, "Payment has been cancelled.", false, null),
    PAYMENT_PROVIDER_UNAVAILABLE(503, "The payment provider is temporarily unavailable. Please try again shortly.", true, 2),
    PAYMENT_PROVIDER_CONFIGURATION_ERROR(503, "Payments are temporarily unavailable.", false, null),
    PAYMENT_CHECKOUT_SESSION_EXPIRED(409, "The checkout session has expired. Please check the order status.", false, null),
    PAYMENT_REFUND_NOT_ALLOWED(409, "This payment cannot be refunded.", false, null),
    PAYMENT_REFUND_IN_PROGRESS(409, "A refund is already in progress.", true, 5),
    PAYMENT_REFUND_FAILED(409, "The refund needs assistance. Please contact support.", false, null),
    PAYMENT_STATE_CONFLICT(409, "Payment state has changed. Please refresh its status.", true, 2),
    PAYMENT_INVALID_REQUEST(400, "The payment request is invalid.", false, null),
    PAYMENT_WEBHOOK_INVALID(400, "The webhook signature or event is invalid.", false, null);

    private final int httpStatus;
    private final String message;
    private final boolean retryable;
    private final Integer retryAfterSeconds;
}
