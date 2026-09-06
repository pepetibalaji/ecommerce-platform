package com.ecommerce.auth.messaging;

import com.ecommerce.auth.entity.AuthOutboxEvent;
import com.ecommerce.auth.repository.AuthOutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Component @RequiredArgsConstructor
public class AuthOutboxPublisher {
  private final AuthOutboxEventRepository outbox;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @Scheduled(fixedDelayString = "${auth.outbox.poll-delay-ms:1000}")
  @Transactional
  public void publishPending() {
    for (AuthOutboxEvent event : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
      try {
        kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
        event.setPublishedAt(Instant.now());
      } catch (Exception ex) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(ex.getClass().getSimpleName());
        log.warn("Auth outbox publish failed. eventId={}, topic={}, attempts={}", event.getId(), event.getTopic(), event.getAttempts());
      }
    }
  }
}
