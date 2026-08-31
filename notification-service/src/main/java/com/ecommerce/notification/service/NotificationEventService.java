package com.ecommerce.notification.service;

import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.repository.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationEventService {
  private final ObjectMapper mapper;
  private final ProcessedEventRepository processed;
  private final NotificationRepository notifications;
  private final PreferenceRepository preferences;

  public NotificationEventService(
      ObjectMapper mapper,
      ProcessedEventRepository processed,
      NotificationRepository notifications,
      PreferenceRepository preferences) {
    this.mapper = mapper;
    this.processed = processed;
    this.notifications = notifications;
    this.preferences = preferences;
  }

  @Transactional
  public void consume(String topic, String body) {
    try {
      JsonNode event = mapper.readTree(body);
      UUID eventId = UUID.fromString(event.required("eventId").asText());
      try {
        processed.saveAndFlush(new ProcessedEvent(eventId));
      } catch (DataIntegrityViolationException duplicate) {
        log.info("Duplicate notification event ignored. eventId={}", eventId);
        return;
      }
      JsonNode user = recipientNode(event, topic);
      if (user == null || user.isNull()) {
        log.warn(
            "Event has no direct recipient; ignored until ownership resolver is configured."
                + " eventId={}, topic={}",
            eventId,
            topic);
        return;
      }
      UUID userId = UUID.fromString(user.asText());
      String type = typeFor(topic);
      if (type == null) {
        return;
      }
      Notification n = new Notification();
      n.setEventId(eventId);
      n.setRecipientUserId(userId);
      n.setChannel(Channel.EMAIL);
      n.setType(type);
      n.setPayload(mapper.writeValueAsString(safePayload(event, type)));
      boolean optedOut =
          preferences
              .findByUserIdAndChannelAndNotificationType(userId, Channel.EMAIL, type)
              .map(p -> !p.isEnabled())
              .orElse(false);
      n.setStatus(optedOut ? NotificationStatus.SKIPPED : NotificationStatus.PENDING);
      notifications.save(n);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid notification event from " + topic, ex);
    }
  }

  private Map<String, Object> safePayload(JsonNode e, String type) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("type", type);
    copy(e, p, "orderId");
    copy(e, p, "paymentId");
    copy(e, p, "refundId");
    copy(e, p, "amount");
    copy(e, p, "currency");
    return p;
  }

  private void copy(JsonNode e, Map<String, Object> p, String name) {
    if (e.hasNonNull(name)) p.put(name, e.get(name).asText());
  }

  private JsonNode recipientNode(JsonNode event, String topic) {
    if ("low-inventory".equals(topic))
      return event.hasNonNull("sellerUserId")
          ? event.get("sellerUserId")
          : event.get("adminUserId");
    return event.get("userId");
  }

  private String typeFor(String t) {
    return switch (t) {
      case "order-created" -> "ORDER_RECEIVED";
      case "payment-success" -> "PAYMENT_SUCCESSFUL";
      case "payment-failed" -> "PAYMENT_FAILED";
      case "order-cancelled" -> "ORDER_CANCELLED";
      case "payment-refund-completed" -> "REFUND_PROCESSED";
      case "order-shipped" -> "ORDER_SHIPPED";
      case "order-delivered" -> "ORDER_DELIVERED";
      case "low-inventory" -> "LOW_STOCK_WARNING";
      case "seller-order-paid" -> "SELLER_NEW_ORDER";
      default -> null;
    };
  }
}
