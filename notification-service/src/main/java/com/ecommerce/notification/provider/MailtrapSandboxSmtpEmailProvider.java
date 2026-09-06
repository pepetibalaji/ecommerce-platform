package com.ecommerce.notification.provider;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Dev-only Mailtrap Email Sandbox adapter. SMTP messages remain visible only in the configured
 * sandbox inbox.
 */
@Component
@ConditionalOnProperty(name = "notification.provider", havingValue = "mailtrap-sandbox")
public class MailtrapSandboxSmtpEmailProvider implements EmailProvider {
  private final JavaMailSender sender;
  private final NotificationProperties properties;
  private final NotificationEmailContent content;

  public MailtrapSandboxSmtpEmailProvider(
      JavaMailSender sender, NotificationProperties properties, NotificationEmailContent content) {
    this.sender = sender;
    this.properties = properties;
    this.content = content;
  }

  @Override
  public String send(String email, Notification notification) throws Exception {
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
}
