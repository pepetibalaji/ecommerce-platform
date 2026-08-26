package com.ecommerce.order.config;

import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.order.observability.PaymentOutcomeMetrics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    DefaultErrorHandler paymentOutcomeKafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            PaymentOutcomeMetrics paymentOutcomeMetrics
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    paymentOutcomeMetrics.deadLettered();
                    return new TopicPartition(KafkaTopics.ORDER_DLQ, record.partition());
                }
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES)
        );
        errorHandler.addNotRetryableExceptions(BadRequestException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                paymentOutcomeMetrics.retry());
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
