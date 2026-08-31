package com.ecommerce.auth.kafka;

import com.ecommerce.auth.entity.User;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.ecommerce.common.events.user.UserContactUpdatedEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserContactEventPublisher {
  private final KafkaTemplate<String, UserContactUpdatedEvent> kafkaTemplate;

  public void publish(User user) {
    UserContactUpdatedEvent event =
        new UserContactUpdatedEvent(
            UUID.randomUUID(),
            user.getId(),
            user.getEmail(),
            user.getStatus().name().equals("ACTIVE"),
            Instant.now());
    kafkaTemplate.send(KafkaTopics.USER_CONTACT_UPDATED, user.getId().toString(), event);
  }
}
