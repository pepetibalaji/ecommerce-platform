package com.ecommerce.payment.kafka.producer;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentRefund;

public interface PaymentEventPublisher {

    void publishPaymentSuccess(Payment payment);

    void publishPaymentFailed(Payment payment);

    void publishRefundCompleted(Payment payment, PaymentRefund refund, java.math.BigDecimal totalRefundedAmount);
}
