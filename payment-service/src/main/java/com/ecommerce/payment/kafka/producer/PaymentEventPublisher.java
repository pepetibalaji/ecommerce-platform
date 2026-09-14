package com.ecommerce.payment.kafka.producer;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentRefund;

public interface PaymentEventPublisher {

    void publishPaymentSuccess(Payment payment);

    void publishPaymentFailed(Payment payment);

    void publishPaymentExpired(Payment payment);

    void publishRefundFailed(Payment payment, PaymentRefund refund);

    void publishRefundCompleted(Payment payment, PaymentRefund refund, java.math.BigDecimal totalRefundedAmount);
}
