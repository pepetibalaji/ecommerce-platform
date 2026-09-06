package com.ecommerce.auth.service;

import com.ecommerce.auth.entity.AuthOutboxEvent;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.repository.AuthOutboxEventRepository;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.common.events.user.UserContactUpdatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Creates transactional-outbox rows; callers must invoke it inside their database transaction. */
@Service
@RequiredArgsConstructor
public class AuthOutboxService {

  private final AuthOutboxEventRepository events;
  private final ObjectMapper objectMapper;

  public void enqueue(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String topic,
      String eventKey,
      Object payload) {
    try {
      events.save(
          AuthOutboxEvent.builder()
              .aggregateType(aggregateType)
              .aggregateId(aggregateId)
              .eventType(eventType)
              .topic(topic)
              .eventKey(eventKey)
              .payload(objectMapper.writeValueAsString(payload))
              .build());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Auth outbox event " + eventType, exception);
    }
  }

  /**
   * This payload must remain compatible with Notification Service's UserContactUpdatedEvent
   * consumer. Do not add raw action tokens or unrelated fields to it.
   */
  public void enqueueUserContactUpdated(User user) {
    enqueue(
        "USER",
        user.getId(),
        "user-contact-updated.v1",
        KafkaTopics.USER_CONTACT_UPDATED,
        user.getId().toString(),
        new UserContactUpdatedEvent(
            UUID.randomUUID(), user.getId(), user.getEmail(), user.isActiveAndVerified(), Instant.now()));
  }
}
