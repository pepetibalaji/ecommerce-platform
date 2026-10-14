package com.ecommerce.notification.provider;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Builds Mailtrap template requests from the notification's send-time payload. */
@Component
public class MailtrapTemplateContent {
  private final NotificationProperties properties;
  private final ObjectMapper mapper;

  public MailtrapTemplateContent(NotificationProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  public String templateUuid(String type) {
    Map<String, String> configured = properties.getMailtrap().getTemplateUuids();
    if (configured == null) return null;
    for (var entry : configured.entrySet()) {
      if (entry.getKey().replace('-', '_').equalsIgnoreCase(type)
          && StringUtils.hasText(entry.getValue())) {
        return entry.getValue();
      }
    }
    return null;
  }

  public Map<String, Object> request(
      String email, Notification notification, String fromEmail, String fromName) throws Exception {
    String uuid = templateUuid(notification.getType());
    if (!StringUtils.hasText(uuid)) {
      throw new IllegalStateException("No Mailtrap template UUID for " + notification.getType());
    }
    return Map.of(
        "from", Map.of("email", fromEmail, "name", fromName),
        "to", List.of(Map.of("email", email)),
        "template_uuid", uuid,
        "template_variables", NotificationTemplateVariables.from(notification, mapper));
  }
}
