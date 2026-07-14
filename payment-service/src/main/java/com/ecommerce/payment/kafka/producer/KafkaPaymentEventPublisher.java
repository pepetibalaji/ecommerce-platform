package com.ecommerce.payment.kafka.producer;

import com.ecommerce.common.events.payment.PaymentFailedEvent;
import com.ecommerce.common.events.payment.PaymentSuccessEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.entity.PaymentAttempt;
import com.ecommerce.payment.enums.PaymentAttemptStatus;
import com.ecommerce.payment.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Override
    public void publishPaymentSuccess(Payment payment) {
        String key = payment.getOrderId().toString();
        String transactionId = resolveTransactionId(payment.getId());

        PaymentSuccessEvent event = new PaymentSuccessEvent(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider() == null ? null : payment.getProvider().name(),
                transactionId,
                payment.getCorrelationId(),
                payment.getTraceId()
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.PAYMENT_SUCCESS, key, event);

        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error(
                        "Failed to publish payment-success. topic={}, key={}, eventId={}, paymentId={}, orderId={}, correlationId={}, traceId={}",
                        KafkaTopics.PAYMENT_SUCCESS,
                        key,
                        event.getEventId(),
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getCorrelationId(),
                        payment.getTraceId(),
                        exception
                );
                return;
            }

            log.info(
                    "Published payment-success. topic={}, partition={}, offset={}, key={}, eventId={}, paymentId={}, orderId={}, transactionId={}, correlationId={}, traceId={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    key,
                    event.getEventId(),
                    payment.getId(),
                    payment.getOrderId(),
                    transactionId,
                    payment.getCorrelationId(),
                    payment.getTraceId()
            );
        });
    }

    @Override
    public void publishPaymentFailed(Payment payment) {
        String key = payment.getOrderId().toString();

        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider() == null ? null : payment.getProvider().name(),
                payment.getStatus() == null ? null : payment.getStatus().name(),
                payment.getFailureReason(),
                payment.getCorrelationId(),
                payment.getTraceId()
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.PAYMENT_FAILED, key, event);

        future.whenComplete((result, exception) -> {
            if (exception != null) {
                log.error(
                        "Failed to publish payment-failed. topic={}, key={}, eventId={}, paymentId={}, orderId={}, status={}, correlationId={}, traceId={}",
                        KafkaTopics.PAYMENT_FAILED,
                        key,
                        event.getEventId(),
                        payment.getId(),
                        payment.getOrderId(),
                        payment.getStatus(),
                        payment.getCorrelationId(),
                        payment.getTraceId(),
                        exception
                );
                return;
            }

            log.info(
                    "Published payment-failed. topic={}, partition={}, offset={}, key={}, eventId={}, paymentId={}, orderId={}, status={}, correlationId={}, traceId={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    key,
                    event.getEventId(),
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getStatus(),
                    payment.getCorrelationId(),
                    payment.getTraceId()
            );
        });
    }

    private String resolveTransactionId(UUID paymentId) {
        return paymentAttemptRepository
                .findTopByPayment_IdAndStatusInOrderByCreatedAtDesc(
                        paymentId,
                        List.of(PaymentAttemptStatus.SUCCESS)
                )
                .map(this::bestProviderTransactionId)
                .orElse(null);
    }

    private String bestProviderTransactionId(PaymentAttempt attempt) {
        if (hasText(attempt.getProviderChargeId())) {
            return attempt.getProviderChargeId();
        }

        if (hasText(attempt.getProviderPaymentIntentId())) {
            return attempt.getProviderPaymentIntentId();
        }

        if (hasText(attempt.getProviderSessionId())) {
            return attempt.getProviderSessionId();
        }

        return attempt.getId() == null ? null : attempt.getId().toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}