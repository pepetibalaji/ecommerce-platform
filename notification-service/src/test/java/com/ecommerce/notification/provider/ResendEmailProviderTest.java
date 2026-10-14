package com.ecommerce.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailProviderTest {
  @Test
  void sendsVerificationThroughConfiguredTemplateWithTheGeneratedLink() throws Exception {
    NotificationProperties properties = configuredProperties();
    properties.getResend().setTemplateIds(Map.of("EMAIL_VERIFICATION", "verify-template"));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.resend.com/emails"))
        .andExpect(header("Authorization", "Bearer re_test"))
        .andExpect(jsonPath("$.from").value("Pepekart <hello@pepekart.example>"))
        .andExpect(jsonPath("$.to[0]").value("user@example.test"))
        .andExpect(jsonPath("$.template.id").value("verify-template"))
        .andExpect(jsonPath("$.template.variables.verificationUrl")
            .value("http://localhost:5173/verify-email?token=secret"))
        .andExpect(jsonPath("$.template.variables.appName").value("Pepekart"))
        .andRespond(withSuccess("{\"id\":\"resend-message-id\"}", MediaType.APPLICATION_JSON));
    ObjectMapper mapper = new ObjectMapper();
    ResendEmailProvider provider = new ResendEmailProvider(
        properties, new NotificationEmailContent(mapper), mapper, builder);

    assertThat(provider.send("user@example.test", notification(
        "EMAIL_VERIFICATION",
        "{\"actionUrl\":\"http://localhost:5173/verify-email?token=secret\"}")))
        .isEqualTo("resend-message-id");
    server.verify();
  }

  @Test
  void sendsUnconfiguredEventAsTextWithoutAResendTemplate() throws Exception {
    NotificationProperties properties = configuredProperties();
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.resend.com/emails"))
        .andExpect(jsonPath("$.subject").value("Your Pepekart order is confirmed"))
        .andExpect(jsonPath("$.text").value("Your Pepekart order is confirmed. Order: order-123"))
        .andExpect(jsonPath("$.template").doesNotExist())
        .andRespond(withSuccess("{\"id\":\"text-message-id\"}", MediaType.APPLICATION_JSON));
    ObjectMapper mapper = new ObjectMapper();
    ResendEmailProvider provider = new ResendEmailProvider(
        properties, new NotificationEmailContent(mapper), mapper, builder);

    assertThat(provider.send("user@example.test", notification(
        "ORDER_RECEIVED", "{\"orderId\":\"order-123\"}")))
        .isEqualTo("text-message-id");
    server.verify();
  }

  @Test
  void requiresCredentialsBeforeSending() {
    NotificationProperties properties = new NotificationProperties();
    ObjectMapper mapper = new ObjectMapper();
    ResendEmailProvider provider = new ResendEmailProvider(
        properties, new NotificationEmailContent(mapper), mapper, RestClient.builder());

    assertThatThrownBy(() -> provider.send("user@example.test", notification("ORDER_RECEIVED", "{}")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Resend API key and from email are required");
  }

  private NotificationProperties configuredProperties() {
    NotificationProperties properties = new NotificationProperties();
    properties.getResend().setApiKey("re_test");
    properties.getResend().setFromEmail("hello@pepekart.example");
    return properties;
  }

  private Notification notification(String type, String payload) {
    Notification notification = new Notification();
    notification.setType(type);
    notification.setPayload(payload);
    return notification;
  }
}
