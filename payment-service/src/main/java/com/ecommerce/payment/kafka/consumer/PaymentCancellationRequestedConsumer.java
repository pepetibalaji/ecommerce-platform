package com.ecommerce.payment.kafka.consumer;

import com.ecommerce.common.events.payment.PaymentCancellationRequestedEvent;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.payment.service.PaymentCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCancellationRequestedConsumer {
    private final PaymentCancellationService service;

    @KafkaListener(topics = KafkaTopics.PAYMENT_CANCELLATION_REQUESTED,
            groupId = "${payment.cancellation-consumer-group:payment-service-cancellations}",
            properties = {"spring.json.value.default.type=com.ecommerce.common.events.payment.PaymentCancellationRequestedEvent",
                    "spring.json.use.type.headers=false"})
    public void onCancellationRequested(PaymentCancellationRequestedEvent event) {
        service.enqueue(event);
    }

    @Scheduled(fixedDelayString = "${payment.cancellations.poll-delay-ms:5000}")
    public void processPending() {
        for (int index = 0; index < 50 && service.processNext(); index++) { /* bounded work */ }
    }
}
