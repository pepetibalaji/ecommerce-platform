package com.ecommerce.notification.provider;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/** Mailtrap transactional Email Sending API adapter. API token stays in runtime secrets. */
@Component
@ConditionalOnProperty(name = "notification.provider", havingValue = "mailtrap")
public class MailtrapEmailProvider implements EmailProvider {
  private static final String SEND_URL = "https://send.api.mailtrap.io/api/send";
  private final RestClient client = RestClient.create();
  private final NotificationProperties properties;
  private final ObjectMapper mapper;

  public MailtrapEmailProvider(NotificationProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  @Override
  public String send(String email, Notification notification) throws Exception {
    var config = properties.getMailtrap();
    if (!StringUtils.hasText(config.getApiToken()) || !StringUtils.hasText(config.getFromEmail()))
      throw new IllegalStateException("Mailtrap API token and from email are required");
    JsonNode response =
        client
            .post()
            .uri(SEND_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + config.getApiToken())
            .body(
                Map.of(
                    "from",
                    Map.of("email", config.getFromEmail(), "name", config.getFromName()),
                    "to",
                    List.of(Map.of("email", email)),
                    "subject",
                    subject(notification),
                    "text",
                    body(notification)))
            .retrieve()
            .body(JsonNode.class);
    JsonNode ids = response == null ? null : response.get("message_ids");
    if (ids == null || ids.isEmpty() || !StringUtils.hasText(ids.get(0).asText()))
      throw new IllegalStateException("Mailtrap accepted no message id");
    return ids.get(0).asText();
  }

  private String subject(Notification n) {
    return switch (n.getType()) {
      case "ORDER_RECEIVED" -> "We received your order";
      case "PAYMENT_SUCCESSFUL" -> "Your payment was successful";
      case "PAYMENT_FAILED" -> "Your payment failed";
      case "ORDER_CANCELLED" -> "Your order was cancelled";
      case "REFUND_PROCESSED" -> "Your refund was processed";
      case "ORDER_SHIPPED" -> "Your order has shipped";
      case "ORDER_DELIVERED" -> "Your order was delivered";
      default -> "Ecommerce Platform notification";
    };
  }

  private String body(Notification n) throws Exception {
    JsonNode payload = mapper.readTree(n.getPayload());
    String orderId = payload.path("orderId").asText("");
    return orderId.isBlank() ? subject(n) : subject(n) + ". Order: " + orderId;
  }
}
