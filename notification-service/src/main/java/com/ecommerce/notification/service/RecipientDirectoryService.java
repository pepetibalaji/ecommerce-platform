package com.ecommerce.notification.service;

import com.ecommerce.common.events.user.UserContactUpdatedEvent;
import com.ecommerce.notification.domain.NotificationRecipient;
import com.ecommerce.notification.domain.ProcessedEvent;
import com.ecommerce.notification.repository.NotificationRecipientRepository;
import com.ecommerce.notification.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipientDirectoryService {
  private final ObjectMapper objectMapper;
  private final ProcessedEventRepository processedEvents;
  private final NotificationRecipientRepository recipients;

  @Transactional
  public void consume(String body) {
    try {
      UserContactUpdatedEvent event = objectMapper.readValue(body, UserContactUpdatedEvent.class);
      try {
        processedEvents.saveAndFlush(new ProcessedEvent(event.eventId()));
      } catch (DataIntegrityViolationException duplicate) {
        log.info("Duplicate user-contact event ignored. eventId={}", event.eventId());
        return;
      }
      NotificationRecipient recipient =
          recipients.findById(event.userId()).orElseGet(NotificationRecipient::new);
      recipient.setUserId(event.userId());
      recipient.setEmail(event.email());
      recipient.setActive(event.active());
      recipient.setUpdatedAt(event.occurredAt());
      recipients.save(recipient);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid user-contact event", exception);
    }
  }

  public Optional<String> findActiveEmail(UUID userId) {
    return recipients.findByUserIdAndActiveTrue(userId).map(NotificationRecipient::getEmail);
  }
}
