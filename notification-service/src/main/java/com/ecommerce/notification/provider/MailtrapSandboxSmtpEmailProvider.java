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

  public MailtrapSandboxSmtpEmailProvider(
      JavaMailSender sender, NotificationProperties properties) {
    this.sender = sender;
    this.properties = properties;
  }

  @Override
  public String send(String email, Notification notification) throws Exception {
    MimeMessage message = sender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
    helper.setFrom(properties.getSandboxFromEmail());
    helper.setTo(email);
    helper.setSubject(subject(notification));
    helper.setText(subject(notification) + ". Notification: " + notification.getId(), false);
    sender.send(message);
    String messageId = message.getMessageID();
    return StringUtils.hasText(messageId) ? messageId : "mailtrap-sandbox-" + notification.getId();
  }

  private String subject(Notification n) {
    return switch (n.getType()) {
      case "ORDER_RECEIVED" -> "We received your order";
      case "PAYMENT_SUCCESSFUL" -> "Your payment was successful";
      case "PAYMENT_FAILED" -> "Your payment failed";
      case "ORDER_CANCELLED" -> "Your order was cancelled";
      case "REFUND_PROCESSED" -> "Your refund was processed";
      default -> "Ecommerce Platform notification";
    };
  }
}
