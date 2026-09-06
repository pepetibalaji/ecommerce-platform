package com.ecommerce.notification.service;

import com.ecommerce.notification.domain.Channel;
import com.ecommerce.notification.domain.Notification;
import com.ecommerce.notification.domain.NotificationStatus;
import com.ecommerce.notification.domain.ProcessedEvent;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.notification.repository.ProcessedEventRepository;
import com.ecommerce.common.events.topic.KafkaTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates durable email intents for Auth identity actions. Kafka contains only action metadata;
 * the delivery worker fetches the one-time token directly from Auth just before sending.
 */
@Slf4j
@Service
public class AuthActionNotificationService {
  public static final String USER_VERIFICATION_REQUESTED = KafkaTopics.AUTH_USER_VERIFICATION_REQUESTED;
  public static final String PASSWORD_RESET_REQUESTED = KafkaTopics.AUTH_PASSWORD_RESET_REQUESTED;
  public static final String EMAIL_CHANGE_REQUESTED = KafkaTopics.AUTH_EMAIL_CHANGE_REQUESTED;

  public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
  public static final String PASSWORD_RESET = "PASSWORD_RESET";
  public static final String EMAIL_CHANGE_CONFIRMATION = "EMAIL_CHANGE_CONFIRMATION";

  private final ObjectMapper mapper;
  private final ProcessedEventRepository processed;
  private final NotificationRepository notifications;

  public AuthActionNotificationService(
      ObjectMapper mapper, ProcessedEventRepository processed, NotificationRepository notifications) {
    this.mapper = mapper;
    this.processed = processed;
    this.notifications = notifications;
  }

  @Transactional
  public void consume(String topic, String body) {
    try {
      JsonNode event = mapper.readTree(body);
      rejectRawTokenFields(event);
      UUID eventId = requiredUuid(event, "eventId");
      UUID userId = requiredUuid(event, "userId");
      String email = requiredText(event, "email");
      ActionDetails action = actionDetails(topic, event);

      try {
        processed.saveAndFlush(new ProcessedEvent(eventId));
      } catch (DataIntegrityViolationException duplicate) {
        log.info("Duplicate Auth identity action event ignored. eventId={}, topic={}", eventId, topic);
        return;
      }

      Notification notification = new Notification();
      notification.setEventId(eventId);
      notification.setRecipientUserId(userId);
      notification.setChannel(Channel.EMAIL);
      notification.setType(action.notificationType());
      // The action ID is safe metadata. Do not add the one-time raw token here.
      notification.setPayload(
          mapper.writeValueAsString(
              Map.of(
                  "actionId", action.actionId(),
                  "deliveryEmail", email,
                  "eventType", topic)));
      notification.setStatus(NotificationStatus.PENDING);
      notifications.save(notification);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid Auth identity action event from " + topic, exception);
    }
  }

  public static boolean isAuthActionNotification(String notificationType) {
    return EMAIL_VERIFICATION.equals(notificationType)
        || PASSWORD_RESET.equals(notificationType)
        || EMAIL_CHANGE_CONFIRMATION.equals(notificationType);
  }

  private ActionDetails actionDetails(String topic, JsonNode event) {
    return switch (topic) {
      case USER_VERIFICATION_REQUESTED ->
          new ActionDetails(EMAIL_VERIFICATION, requiredUuid(event, "verificationActionId"));
      case PASSWORD_RESET_REQUESTED ->
          new ActionDetails(PASSWORD_RESET, requiredUuid(event, "passwordResetActionId"));
      case EMAIL_CHANGE_REQUESTED ->
          new ActionDetails(EMAIL_CHANGE_CONFIRMATION, requiredUuid(event, "emailChangeActionId"));
      default -> throw new IllegalArgumentException("Unsupported Auth identity action topic");
    };
  }

  private UUID requiredUuid(JsonNode event, String field) {
    try {
      return UUID.fromString(requiredText(event, field));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid " + field, exception);
    }
  }

  private String requiredText(JsonNode event, String field) {
    String value = event.path(field).asText();
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing " + field);
    }
    return value;
  }

  private void rejectRawTokenFields(JsonNode node) {
    if (node.isObject()) {
      var names = node.fieldNames();
      while (names.hasNext()) {
        String name = names.next();
        if ("token".equalsIgnoreCase(name) || "deliveryToken".equalsIgnoreCase(name)) {
          throw new IllegalArgumentException("Raw delivery tokens are not allowed in Kafka events");
        }
        rejectRawTokenFields(node.get(name));
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        rejectRawTokenFields(child);
      }
    }
  }

  private record ActionDetails(String notificationType, UUID actionId) {}
}
