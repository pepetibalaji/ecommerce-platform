package com.ecommerce.payment.kafka.producer;

import com.ecommerce.common.events.payment.PaymentRefundRequestRejectedEvent;
import com.ecommerce.common.events.payment.PaymentRefundRequestedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Publishes a terminal business rejection back to Order Service. */
@Component
@RequiredArgsConstructor
public class PaymentRefundRequestOutcomePublisher {

    private final com.ecommerce.payment.outbox.PaymentOutboxStore outbox;

    @org.springframework.transaction.annotation.Transactional
    public CompletableFuture<SendResult<String, Object>> publishRejected(
            PaymentRefundRequestedEvent request,
            String reason
    ) {
        PaymentRefundRequestRejectedEvent event = new PaymentRefundRequestRejectedEvent(
                request.getRefundRequestId(),
                request.getPaymentId(),
                request.getOrderId(),
                reason,
                request.getCorrelationId(),
                request.getTraceId()
        );
        outbox.enqueue("refund-request:" + request.getRefundRequestId() + ":rejected", request.getPaymentId(),
                request.getOrderId(), KafkaTopics.PAYMENT_REFUND_REQUEST_REJECTED, event);
        return CompletableFuture.completedFuture(null);
    }
}
