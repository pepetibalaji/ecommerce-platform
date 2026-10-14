package com.ecommerce.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MailtrapTemplateContentTest {
  @Test
  void selectsConfiguredTemplateAndOnlyExposesApprovedOrderFields() throws Exception {
    NotificationProperties properties = new NotificationProperties();
    properties.getMailtrap().setTemplateUuids(Map.of("order-received", "order-template-id"));
    MailtrapTemplateContent templates = new MailtrapTemplateContent(properties, new ObjectMapper());
    Notification notification = notification(
        "ORDER_RECEIVED",
        "{\"orderId\":\"order-123\",\"amount\":\"42.50\",\"currency\":\"USD\",\"userId\":\"private-user-id\"}");

    assertThat(templates.templateUuid("ORDER_RECEIVED")).isEqualTo("order-template-id");
    Map<String, Object> request =
        templates.request("customer@example.test", notification, "from@example.test", "Pepekart");
    assertThat(request.get("template_uuid")).isEqualTo("order-template-id");
    assertThat(variables(request))
        .containsEntry("appName", "Pepekart")
        .containsEntry("orderId", "order-123")
        .containsEntry("amount", "42.50")
        .containsEntry("currency", "USD")
        .doesNotContainKey("userId");
  }

  @Test
  void mapsEachIdentityActionToItsOwnLinkVariable() throws Exception {
    NotificationProperties properties = new NotificationProperties();
    properties.getMailtrap().setTemplateUuids(Map.of(
        "EMAIL_VERIFICATION", "verify-id",
        "PASSWORD_RESET", "reset-id",
        "EMAIL_CHANGE_CONFIRMATION", "change-id"));
    MailtrapTemplateContent templates = new MailtrapTemplateContent(properties, new ObjectMapper());

    assertLinkVariable(templates, "EMAIL_VERIFICATION", "verificationUrl");
    assertLinkVariable(templates, "PASSWORD_RESET", "resetUrl");
    assertLinkVariable(templates, "EMAIL_CHANGE_CONFIRMATION", "confirmationUrl");
    assertThatThrownBy(() -> templates.request(
        "user@example.test", notification("PASSWORD_RESET", "{}"), "from@example.test", "Pepekart"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Action URL is required");
  }

  private void assertLinkVariable(MailtrapTemplateContent templates, String type, String variable)
      throws Exception {
    Map<String, Object> request = templates.request(
        "user@example.test", notification(type, "{\"actionUrl\":\"https://shop.example.test/action?token=secret\"}"),
        "from@example.test", "Pepekart");
    assertThat(variables(request))
        .containsEntry(variable, "https://shop.example.test/action?token=secret")
        .doesNotContainKey("actionUrl");
  }

  private Notification notification(String type, String payload) {
    Notification notification = new Notification();
    notification.setType(type);
    notification.setPayload(payload);
    return notification;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> variables(Map<String, Object> request) {
    return (Map<String, Object>) request.get("template_variables");
  }
}
