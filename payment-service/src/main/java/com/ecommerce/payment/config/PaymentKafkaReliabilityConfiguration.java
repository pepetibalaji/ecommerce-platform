package com.ecommerce.payment.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class PaymentKafkaReliabilityConfiguration {
    @Bean
    @SuppressWarnings("unchecked")
    org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer paymentEventJsonSerializer(
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return factory -> ((org.springframework.kafka.core.DefaultKafkaProducerFactory<Object,Object>)factory)
                .setValueSerializer(new org.springframework.kafka.support.serializer.JsonSerializer<>(
                        mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)));
    }

    @Bean DefaultErrorHandler paymentCommandErrorHandler(KafkaTemplate<Object,Object> kafka,MeterRegistry metrics) {
        var recoverer=new DeadLetterPublishingRecoverer(kafka,(record,error)->{
            metrics.counter("payment.command.dlq","topic",record.topic()).increment();
            log.error("payment_command_dlq topic={} partition={} offset={} errorType={}",record.topic(),record.partition(),record.offset(),error.getClass().getSimpleName());
            return new TopicPartition(record.topic()+".DLT",record.partition());
        });
        recoverer.setFailIfSendResultIsError(true);
        var handler=new DefaultErrorHandler(recoverer,new FixedBackOff(2000,8));
        handler.setRetryListeners((record,error,attempt)->metrics.counter("payment.command.retry","topic",record.topic()).increment());
        handler.setCommitRecovered(true);
        return handler;
    }
}
