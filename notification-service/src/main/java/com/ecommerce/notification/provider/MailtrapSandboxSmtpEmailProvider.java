package com.ecommerce.notification.provider;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-only Mailtrap Email Sandbox adapter. Configured templates use the Sandbox API; unconfigured
 * notification types use SMTP. Both remain in the sandbox inbox.
 */
@Component
@ConditionalOnProperty(name = "notification.provider", havingValue = "mailtrap-sandbox")
public class MailtrapSandboxSmtpEmailProvider implements EmailProvider {
  private static final Logger log = LoggerFactory.getLogger(MailtrapSandboxSmtpEmailProvider.class);
  private final JavaMailSender sender;
  private final NotificationProperties properties;
  private final NotificationEmailContent content;
  private final MailtrapTemplateContent templates;
  private final RestClient client;
  private volatile boolean templateApiUnauthorized;

  public MailtrapSandboxSmtpEmailProvider(
      JavaMailSender sender,
      NotificationProperties properties,
      NotificationEmailContent content,
      MailtrapTemplateContent templates,
      RestClient.Builder clientBuilder) {
    this.sender = sender;
    this.properties = properties;
    this.content = content;
    this.templates = templates;
    this.client = clientBuilder.build();
  }

  @Override
  public String send(String email, Notification notification) throws Exception {
    if (!templateApiUnauthorized
        && StringUtils.hasText(templates.templateUuid(notification.getType()))
        && StringUtils.hasText(properties.getMailtrap().getApiToken())) {
      try {
        return sendTemplate(email, notification);
      } catch (HttpClientErrorException exception) {
        int status = exception.getStatusCode().value();
        if (status != 401 && status != 403) throw exception;
        templateApiUnauthorized = true;
        log.warn("Mailtrap Sandbox template API rejected credentials (HTTP {}); using SMTP until restart", status);
      }
    }
    return sendSmtp(email, notification);
  }

  private String sendSmtp(String email, Notification notification) throws Exception {
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
    helper.setFrom(properties.getSandboxFromEmail());
    helper.setTo(email);
    helper.setSubject(content.subject(notification));
    helper.setText(content.body(notification), false);
    sender.send(message);
    String messageId = message.getMessageID();
    return StringUtils.hasText(messageId) ? messageId : "mailtrap-sandbox-" + notification.getId();
  }

  private String sendTemplate(String email, Notification notification) throws Exception {
    var config = properties.getMailtrap();
    if (config.getSandboxInboxId() == null || config.getSandboxInboxId() <= 0) {
      throw new IllegalStateException("Mailtrap sandbox inbox ID is required for templates");
    }
    JsonNode response =
        client
            .post()
            .uri("https://sandbox.api.mailtrap.io/api/send/{inboxId}", config.getSandboxInboxId())
            .contentType(MediaType.APPLICATION_JSON)
            .header("Api-Token", config.getApiToken())
            .body(templates.request(email, notification, properties.getSandboxFromEmail(), "Pepekart"))
            .retrieve()
            .body(JsonNode.class);
    JsonNode ids = response == null ? null : response.path("message_ids");
    if (response == null || !response.path("success").asBoolean()
        || ids == null || !ids.isArray() || ids.isEmpty()
        || !StringUtils.hasText(ids.get(0).asText())) {
      throw new IllegalStateException("Mailtrap sandbox accepted no template message ID");
    }
    return ids.get(0).asText();
  }
}
