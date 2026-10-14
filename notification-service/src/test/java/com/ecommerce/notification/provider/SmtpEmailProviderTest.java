package com.ecommerce.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.config.SmtpEmailConfiguration;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailProviderTest {
  @Test
  void rendersVerificationHtmlWithTheUsersLinkAndSendsItOverSmtp() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    NotificationEmailContent plain = new NotificationEmailContent(mapper);
    SmtpEmailContent html = new SmtpEmailContent(mapper, plain);
    Notification notification = new Notification();
    notification.setType("EMAIL_VERIFICATION");
    notification.setPayload(
        "{\"actionUrl\":\"http://localhost:5173/verify-email?token=one-user-token\"}");

    String rendered = html.html(notification);
    assertThat(rendered)
        .contains("Pepekart", "Verify my email")
        .contains("href=\"http://localhost:5173/verify-email?token=one-user-token\"")
        .doesNotContain("{{{verificationUrl}}}");

    JavaMailSender sender = mock(JavaMailSender.class);
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    when(sender.createMimeMessage()).thenReturn(message);
    doAnswer(invocation -> { message.saveChanges(); return null; }).when(sender).send(message);
    NotificationProperties properties = new NotificationProperties();
    SmtpEmailProvider provider = new SmtpEmailProvider(sender, properties, plain, html);

    assertThat(provider.send("new-user@example.test", notification)).isNotBlank();
    assertThat(message.getSubject()).isEqualTo("Verify your email address");
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo("new-user@example.test");
    verify(sender).send(message);
  }

  @Test
  void escapesEventFieldsInHtml() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    SmtpEmailContent html = new SmtpEmailContent(mapper, new NotificationEmailContent(mapper));
    Notification notification = new Notification();
    notification.setType("ORDER_RECEIVED");
    notification.setPayload("{\"orderId\":\"<script>alert(1)</script>\"}");

    assertThat(html.html(notification))
        .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
        .doesNotContain("<script>");
  }

  @Test
  void includesPaymentDetailsInTheDesignedEmail() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    SmtpEmailContent html = new SmtpEmailContent(mapper, new NotificationEmailContent(mapper));
    Notification notification = new Notification();
    notification.setType("PAYMENT_SUCCESSFUL");
    notification.setPayload("{\"orderId\":\"order-42\",\"paymentId\":\"pay-9\","
        + "\"amount\":\"1499\",\"currency\":\"INR\"}");

    assertThat(html.html(notification))
        .contains("Your payment went through", "order-42", "pay-9", "1499", "INR")
        .contains("Pepekart");
  }

  @Test
  void configuresDedicatedSmtpConnectionWithoutMailtrapSettings() {
    NotificationProperties properties = new NotificationProperties();
    properties.getSmtp().setHost("mailpit");
    properties.getSmtp().setPort(1025);

    JavaMailSenderImpl sender = (JavaMailSenderImpl)
        new SmtpEmailConfiguration().notificationSmtpSender(properties);

    assertThat(sender.getHost()).isEqualTo("mailpit");
    assertThat(sender.getPort()).isEqualTo(1025);
    assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "false");
    assertThat(sender.getJavaMailProperties())
        .containsEntry("mail.smtp.starttls.enable", "false");
  }
}
