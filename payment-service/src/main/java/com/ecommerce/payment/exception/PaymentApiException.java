package com.ecommerce.payment.exception;

import lombok.Getter;

@Getter
public class PaymentApiException extends RuntimeException {
    private final PaymentErrorCode code;

    public PaymentApiException(PaymentErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public PaymentApiException(PaymentErrorCode code, Throwable cause) {
        super(code.getMessage(), cause);
        this.code = code;
    }
}
