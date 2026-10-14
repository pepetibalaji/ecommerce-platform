package com.ecommerce.notification.provider;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.Notification;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Configurable SMTP sender for local Mailpit and other SMTP services. */
@Component
@ConditionalOnProperty(name = "notification.provider", havingValue = "smtp")
public class SmtpEmailProvider implements EmailProvider {
  private final JavaMailSender sender;
  private final NotificationProperties properties;
  private final NotificationEmailContent text;
  private final SmtpEmailContent html;

  public SmtpEmailProvider(@Qualifier("notificationSmtpSender") JavaMailSender sender,
      NotificationProperties properties,
      NotificationEmailContent text, SmtpEmailContent html) {
    this.sender = sender;
    this.properties = properties;
    this.text = text;
    this.html = html;
  }

  @Override
  public String send(String email, Notification notification) throws Exception {
    var config = properties.getSmtp();
    if (!StringUtils.hasText(config.getFromEmail())) {
      throw new IllegalStateException("SMTP from email is required");
    }
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setFrom(config.getFromEmail(), config.getFromName());
    helper.setTo(email);
    helper.setSubject(text.subject(notification));
    helper.setText(text.body(notification), html.html(notification));
    sender.send(message);
    String messageId = message.getMessageID();
    return StringUtils.hasText(messageId) ? messageId : "smtp-" + notification.getId();
  }
}
