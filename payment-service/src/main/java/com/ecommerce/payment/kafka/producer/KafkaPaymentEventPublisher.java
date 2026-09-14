package com.ecommerce.payment.kafka.producer;

import com.ecommerce.common.events.payment.*;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.outbox.PaymentOutboxStore;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

/** Compatibility name; persists in the caller transaction, delivers through the worker. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {
    private final PaymentOutboxStore outbox;
    private final PaymentAttemptRepository attempts;

    public void publishPaymentSuccess(Payment payment) {
        String transaction = attempts.findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(payment.getId(),
                List.of(PaymentAttemptStatus.SUCCESS)).map(PaymentAttempt::getProviderPaymentIntentId).orElse(null);
        var event = new PaymentSuccessEvent(payment.getId(),payment.getOrderId(),payment.getUserId(),payment.getAmount(),
                payment.getCurrency(),payment.getProvider().name(),transaction,payment.getCorrelationId(),payment.getTraceId());
        outbox.enqueue(resultKey(payment),payment.getId(),payment.getOrderId(),KafkaTopics.PAYMENT_SUCCESS,event);
    }

    public void publishPaymentFailed(Payment payment) {
        var event = new PaymentFailedEvent(payment.getId(),payment.getOrderId(),payment.getUserId(),payment.getAmount(),
                payment.getCurrency(),payment.getProvider().name(),"PAYMENT_"+payment.getStatus().name(),"PAYMENT_"+payment.getStatus().name(),
                payment.getCorrelationId(),payment.getTraceId());
        outbox.enqueue(resultKey(payment),payment.getId(),payment.getOrderId(),KafkaTopics.PAYMENT_FAILED,event);
    }

    public void publishPaymentExpired(Payment payment) {
        var event = new PaymentExpiredEvent(payment.getId(),payment.getOrderId(),payment.getUserId(),payment.getAmount(),
                payment.getCurrency(),payment.getProvider().name(),payment.getCorrelationId(),payment.getTraceId());
        outbox.enqueue(resultKey(payment),payment.getId(),payment.getOrderId(),KafkaTopics.PAYMENT_EXPIRED,event);
    }

    public void publishRefundCompleted(Payment payment, PaymentRefund refund, BigDecimal total) {
        // Repair legacy paid rows before the refund outcome can overtake their missing success.
        publishPaymentSuccess(payment);
        var event = new PaymentRefundCompletedEvent(refund.getId(),payment.getId(),payment.getOrderId(),payment.getUserId(),
                refund.getAmount(),total,payment.getAmount(),payment.getCurrency(),correlation(payment,refund),trace(payment,refund));
        event.setProvider(payment.getProvider().name());
        outbox.enqueue("refund:"+refund.getId()+":completed",payment.getId(),payment.getOrderId(),KafkaTopics.PAYMENT_REFUND_COMPLETED,event);
    }

    public void publishRefundFailed(Payment payment, PaymentRefund refund) {
        publishPaymentSuccess(payment);
        var event = new PaymentRefundFailedEvent(refund.getId(),payment.getId(),payment.getOrderId(),payment.getUserId(),
                refund.getAmount(),payment.getCurrency(),payment.getProvider().name(),refund.getStatus().name(),
                correlation(payment,refund),trace(payment,refund));
        event.setRefundRequestId(refund.getRefundRequestId());
        outbox.enqueue("refund:"+refund.getId()+":failed",payment.getId(),payment.getOrderId(),KafkaTopics.PAYMENT_REFUND_FAILED,event);
    }

    private String resultKey(Payment payment) { return "payment:"+payment.getId()+":result"; }
    private String correlation(Payment p, PaymentRefund r) { return r.getCorrelationId()==null?p.getCorrelationId():r.getCorrelationId(); }
    private String trace(Payment p, PaymentRefund r) { return r.getTraceId()==null?p.getTraceId():r.getTraceId(); }
}
