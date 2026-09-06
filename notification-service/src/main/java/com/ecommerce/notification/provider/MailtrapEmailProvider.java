package com.ecommerce.notification.provider;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
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
  private final NotificationEmailContent content;

  public MailtrapEmailProvider(NotificationProperties properties, NotificationEmailContent content) {
    this.properties = properties;
    this.content = content;
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
                    content.subject(notification),
                    "text",
                    content.body(notification)))
            .retrieve()
            .body(JsonNode.class);
    JsonNode ids = response == null ? null : response.get("message_ids");
    if (ids == null || ids.isEmpty() || !StringUtils.hasText(ids.get(0).asText()))
      throw new IllegalStateException("Mailtrap accepted no message id");
    return ids.get(0).asText();
  }

}
