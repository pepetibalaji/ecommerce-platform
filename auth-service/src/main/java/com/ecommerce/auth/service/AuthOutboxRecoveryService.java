package com.ecommerce.auth.service;

import com.ecommerce.auth.repository.AuthOutboxEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit operator recovery path for terminal outbox failures. */
@Service
@RequiredArgsConstructor
public class AuthOutboxRecoveryService {
  private final AuthOutboxEventRepository events;

  @Transactional
  public int replayDeadLetters() {
    var deadLetters = events.findTop100ByDeadLetteredAtIsNotNullOrderByCreatedAtAsc();
    deadLetters.forEach(event -> {
      event.setDeadLetteredAt(null);
      event.setAttempts(0);
      event.setLastError(null);
      event.setNextAttemptAt(Instant.now());
    });
    return deadLetters.size();
  }
}
