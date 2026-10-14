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

/** Sends notifications through Resend, using a published template when one is configured. */
@Component
@ConditionalOnProperty(name = "notification.provider", havingValue = "resend")
public class ResendEmailProvider implements EmailProvider {
  private static final String SEND_URL = "https://api.resend.com/emails";
  private final NotificationProperties properties;
  private final NotificationEmailContent content;
  private final ObjectMapper mapper;
  private final RestClient client;

  public ResendEmailProvider(
      NotificationProperties properties,
      NotificationEmailContent content,
      ObjectMapper mapper,
      RestClient.Builder clientBuilder) {
    this.properties = properties;
    this.content = content;
    this.mapper = mapper;
    this.client = clientBuilder.build();
  }

  @Override
  public String send(String email, Notification notification) throws Exception {
    var config = properties.getResend();
    if (!StringUtils.hasText(config.getApiKey()) || !StringUtils.hasText(config.getFromEmail())) {
      throw new IllegalStateException("Resend API key and from email are required");
    }
    String from = StringUtils.hasText(config.getFromName())
        ? config.getFromName() + " <" + config.getFromEmail() + ">"
        : config.getFromEmail();
    String templateId = templateId(notification.getType(), config.getTemplateIds());
    Object body = StringUtils.hasText(templateId)
        ? Map.of(
            "from", from,
            "to", List.of(email),
            "template", Map.of(
                "id", templateId,
                "variables", NotificationTemplateVariables.from(notification, mapper)))
        : Map.of(
            "from", from,
            "to", List.of(email),
            "subject", content.subject(notification),
            "text", content.body(notification));
    JsonNode response = client.post()
        .uri(SEND_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + config.getApiKey())
        .body(body)
        .retrieve()
        .body(JsonNode.class);
    String id = response == null ? null : response.path("id").asText();
    if (!StringUtils.hasText(id)) {
      throw new IllegalStateException("Resend accepted no message id");
    }
    return id;
  }

  private String templateId(String type, Map<String, String> configured) {
    if (configured == null) return null;
    for (var entry : configured.entrySet()) {
      if (entry.getKey().replace('-', '_').equalsIgnoreCase(type)
          && StringUtils.hasText(entry.getValue())) {
        return entry.getValue();
      }
    }
    return null;
  }
}
