package com.ecommerce.notification.provider;

import com.ecommerce.notification.domain.Notification;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "notification.provider",
    havingValue = "logging-email",
    matchIfMissing = true)
public class LoggingEmailProvider implements EmailProvider {
  public String send(String email, Notification notification) {
    String ref = "local-" + UUID.randomUUID();
    log.info(
        "Email accepted. notificationId={}, recipient={}, type={}, providerMessageId={}",
        notification.getId(),
        email,
        notification.getType(),
        ref);
    return ref;
  }
}
