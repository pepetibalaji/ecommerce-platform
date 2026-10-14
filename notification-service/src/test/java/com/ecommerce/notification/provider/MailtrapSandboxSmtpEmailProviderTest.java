package com.ecommerce.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.mockito.Mockito.when;

class MailtrapSandboxSmtpEmailProviderTest {
  @Test
  void sendsVerificationUsingTheSavedTemplateAndGeneratedLink() throws Exception {
    JavaMailSender smtp = mock(JavaMailSender.class);
    NotificationProperties properties = new NotificationProperties();
    properties.setSandboxFromEmail("sandbox@pepekart.test");
    properties.getMailtrap().setApiToken("sandbox-api-token");
    properties.getMailtrap().setSandboxInboxId(4883696L);
    properties.getMailtrap().setTemplateUuids(
        Map.of("EMAIL_VERIFICATION", "31a79ec0-e191-49d6-a730-4a50b30da69d"));

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://sandbox.api.mailtrap.io/api/send/4883696"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Api-Token", "sandbox-api-token"))
        .andExpect(jsonPath("$.from.email").value("sandbox@pepekart.test"))
        .andExpect(jsonPath("$.from.name").value("Pepekart"))
        .andExpect(jsonPath("$.to[0].email").value("new-user@pepekart.test"))
        .andExpect(jsonPath("$.template_uuid").value("31a79ec0-e191-49d6-a730-4a50b30da69d"))
        .andExpect(jsonPath("$.template_variables.verificationUrl")
            .value("http://localhost:5173/verify-email?token=one-time-secret"))
        .andRespond(withSuccess("{\"success\":true,\"message_ids\":[\"sandbox-message-id\"]}", MediaType.APPLICATION_JSON));

    Notification notification = new Notification();
    notification.setType("EMAIL_VERIFICATION");
    notification.setPayload("{\"actionUrl\":\"http://localhost:5173/verify-email?token=one-time-secret\"}");
    var provider =
        new MailtrapSandboxSmtpEmailProvider(
            smtp, properties, new NotificationEmailContent(new ObjectMapper()),
            new MailtrapTemplateContent(properties, new ObjectMapper()), builder);

    assertThat(provider.send("new-user@pepekart.test", notification))
        .isEqualTo("sandbox-message-id");
    server.verify();
    verifyNoInteractions(smtp);
  }

  @Test
  void fallsBackToSmtpAfterTemplateApiRejectsTheToken() throws Exception {
    JavaMailSender smtp = mock(JavaMailSender.class);
    MimeMessage smtpMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(smtp.createMimeMessage()).thenReturn(smtpMessage);
    NotificationProperties properties = new NotificationProperties();
    properties.setSandboxFromEmail("sandbox@pepekart.test");
    properties.getMailtrap().setApiToken("rejected-api-token");
    properties.getMailtrap().setSandboxInboxId(4883696L);
    properties.getMailtrap().setTemplateUuids(Map.of("EMAIL_VERIFICATION", "verify-template-id"));
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://sandbox.api.mailtrap.io/api/send/4883696"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
            .body("{\"success\":false,\"errors\":[\"Too many failed login attempts\"]}"));
    ObjectMapper mapper = new ObjectMapper();
    var provider = new MailtrapSandboxSmtpEmailProvider(
        smtp, properties, new NotificationEmailContent(mapper),
        new MailtrapTemplateContent(properties, mapper), builder);
    Notification notification = new Notification();
    notification.setId(UUID.randomUUID());
    notification.setType("EMAIL_VERIFICATION");
    notification.setPayload("{\"actionUrl\":\"http://localhost:5173/verify-email?token=one-time-secret\"}");

    provider.send("new-user@pepekart.test", notification);
    provider.send("new-user@pepekart.test", notification);

    server.verify();
    verify(smtp, times(2)).send(smtpMessage);
    assertThat(smtpMessage.getContent().toString()).contains("one-time-secret");
  }
}
