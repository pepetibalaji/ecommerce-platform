package com.ecommerce.payment.config;

import com.ecommerce.payment.exception.PaymentApiException;
import com.ecommerce.payment.exception.PaymentErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaymentPagination {
    private PaymentPagination() {}
    public static Pageable page(int page, int size) {
        if (page < 0 || size < 1 || size > 50)
            throw new PaymentApiException(PaymentErrorCode.PAYMENT_INVALID_REQUEST);
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
    public static Pageable bounded(Pageable pageable) {
        return page(pageable.getPageNumber(), pageable.getPageSize());
    }
}
