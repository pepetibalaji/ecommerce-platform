package com.ecommerce.payment.exception;

public class PaymentConfirmationUnavailableException extends RuntimeException {
    public PaymentConfirmationUnavailableException() { super("Payment confirmation is temporarily unavailable. Do not pay again; retry the status check."); }
}
