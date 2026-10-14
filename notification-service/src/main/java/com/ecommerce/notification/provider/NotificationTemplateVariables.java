package com.ecommerce.notification.provider;

import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Variables shared by hosted email templates without exposing private event payload fields. */
final class NotificationTemplateVariables {
  private NotificationTemplateVariables() {}

  static Map<String, Object> from(Notification notification, ObjectMapper mapper) throws Exception {
    JsonNode payload = mapper.readTree(notification.getPayload());
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("appName", "Pepekart");
    variables.put("notificationType", notification.getType());
    switch (notification.getType()) {
      case "EMAIL_VERIFICATION" -> actionUrl(payload, variables, "verificationUrl");
      case "PASSWORD_RESET" -> actionUrl(payload, variables, "resetUrl");
      case "EMAIL_CHANGE_CONFIRMATION" -> actionUrl(payload, variables, "confirmationUrl");
      default -> {
        for (String field : List.of(
            "orderId", "paymentId", "refundId", "amount", "currency", "productId", "availableStock")) {
          String value = payload.path(field).asText();
          if (StringUtils.hasText(value)) variables.put(field, value);
        }
      }
    }
    return variables;
  }

  private static void actionUrl(JsonNode payload, Map<String, Object> variables, String variable) {
    String url = payload.path("actionUrl").asText();
    if (!StringUtils.hasText(url)) {
      throw new IllegalStateException("Action URL is required for " + variable);
    }
    variables.put(variable, url);
  }
}
