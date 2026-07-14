package com.ecommerce.payment.kafka.producer;

import com.ecommerce.payment.entity.Payment;

public interface PaymentEventPublisher {

    void publishPaymentSuccess(Payment payment);

    void publishPaymentFailed(Payment payment);
}