package com.ecommerce.payment.kafka.consumer;

import com.ecommerce.common.events.payment.PaymentRefundRequestedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.kafka.producer.PaymentRefundRequestOutcomePublisher;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Handles the order-side cancellation command. Provider calls are delegated to the existing
 * payment refund flow, whose idempotency key is deterministically derived from refundRequestId.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundRequestedConsumer {

    private final PaymentRefundService paymentRefundService;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRequestOutcomePublisher outcomePublisher;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_REFUND_REQUESTED,
            groupId = "${payment.refund-request-consumer-group:payment-service-refund-requests}"
    )
    public void onRefundRequested(
            PaymentRefundRequestedEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key
    ) {
        putMdc(event);
        try {
            validate(event);
            Payment payment = paymentRepository.findByIdAndOrderId(event.getPaymentId(), event.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Payment not found for paymentId=" + event.getPaymentId()
                                    + ", orderId=" + event.getOrderId()));
            if (!payment.getUserId().equals(event.getUserId())) {
                throw new BadRequestException("Refund request user does not own the payment");
            }

            var result = paymentRefundService.refundPayment(
                    event.getPaymentId(),
                    event.getOrderId(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getReason(),
                    "order-refund-request:" + event.getRefundRequestId()
            );
            log.info("Processed payment-refund-requested. key={}, refundRequestId={}, orderId={}, paymentId={}, status={}",
                    key, event.getRefundRequestId(), event.getOrderId(), event.getPaymentId(), result.status());

            if ("REFUND_FAILED".equals(result.status())) {
                publishRejectionOrRetry(event, result.failureReason());
            }
        } catch (BadRequestException | ResourceNotFoundException exception) {
            // These are terminal business refusals, not transient Kafka processing failures.
            log.warn("Refusing payment-refund-requested. refundRequestId={}, orderId={}, paymentId={}, reason={}",
                    event == null ? null : event.getRefundRequestId(),
                    event == null ? null : event.getOrderId(),
                    event == null ? null : event.getPaymentId(), exception.getMessage());
            publishRejectionOrRetry(event, exception.getMessage());
        } finally {
            clearMdc();
        }
    }

    private void validate(PaymentRefundRequestedEvent event) {
        if (event == null || event.getRefundRequestId() == null || event.getPaymentId() == null
                || event.getOrderId() == null || event.getUserId() == null || event.getAmount() == null
                || event.getCurrency() == null || event.getCurrency().isBlank()) {
            throw new BadRequestException("Refund request event is missing a required field");
        }
        if (event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund request amount must be greater than zero");
        }
    }

    private void publishRejectionOrRetry(PaymentRefundRequestedEvent event, String reason) {
        if (event == null || event.getOrderId() == null) {
            // No safe response key exists. Let Kafka retry/DLQ the malformed record.
            throw new IllegalStateException("Cannot publish rejection for a refund request without an order id");
        }
        try {
            outcomePublisher.publishRejected(event,
                    reason == null || reason.isBlank() ? "Payment Service rejected the refund request" : reason).get();
        } catch (Exception publishFailure) {
            // Throwing preserves at-least-once delivery: the command will be retried and the
            // Payment Service idempotency key prevents duplicate provider refunds.
            throw new IllegalStateException("Could not publish payment refund rejection", publishFailure);
        }
    }

    private void putMdc(PaymentRefundRequestedEvent event) {
        if (event == null) {
            return;
        }
        put("correlationId", event.getCorrelationId());
        put("traceId", event.getTraceId());
        put("eventId", event.getEventId() == null ? null : event.getEventId().toString());
        put("orderId", event.getOrderId() == null ? null : event.getOrderId().toString());
        put("paymentId", event.getPaymentId() == null ? null : event.getPaymentId().toString());
    }

    private void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private void clearMdc() {
        MDC.remove("correlationId");
        MDC.remove("traceId");
        MDC.remove("eventId");
        MDC.remove("orderId");
        MDC.remove("paymentId");
    }
}
