package com.ecommerce.auth.messaging;

import com.ecommerce.auth.entity.AuthOutboxEvent;
import com.ecommerce.auth.repository.AuthOutboxEventRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
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

  @org.springframework.beans.factory.annotation.Value("${auth.outbox.max-attempts:10}")
  private int maxAttempts;
  @org.springframework.beans.factory.annotation.Value("${auth.outbox.lease-duration:PT1M}")
  private Duration leaseDuration;
  @org.springframework.beans.factory.annotation.Value("${auth.outbox.retry-base-delay:PT5S}")
  private Duration retryBaseDelay;

  @Scheduled(fixedDelayString = "${auth.outbox.poll-delay-ms:1000}")
  @Transactional
  public void publishPending() {
    Instant now = Instant.now();
    String workerId = UUID.randomUUID().toString();
    for (AuthOutboxEvent event : outbox.claimable(now, 100)) {
      event.setLeaseOwner(workerId);
      event.setLeaseUntil(now.plus(leaseDuration));
      try {
        kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get();
        event.setPublishedAt(Instant.now());
        event.setLeaseOwner(null);
        event.setLeaseUntil(null);
      } catch (Exception ex) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(ex.getClass().getSimpleName());
        event.setLeaseOwner(null);
        event.setLeaseUntil(null);
        if (event.getAttempts() >= maxAttempts) {
          event.setDeadLetteredAt(Instant.now());
          log.error("Auth outbox event exhausted retries. eventId={}, topic={}", event.getId(), event.getTopic());
        } else {
          long multiplier = 1L << Math.min(event.getAttempts() - 1, 10);
          event.setNextAttemptAt(Instant.now().plus(retryBaseDelay.multipliedBy(multiplier)));
          log.warn("Auth outbox publish failed. eventId={}, topic={}, attempts={}", event.getId(), event.getTopic(), event.getAttempts());
        }
      }
    }
  }
}
