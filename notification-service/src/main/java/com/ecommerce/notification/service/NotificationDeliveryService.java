package com.ecommerce.notification.service;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.provider.EmailProvider;
import com.ecommerce.notification.repository.*;
import io.micrometer.core.instrument.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationDeliveryService {
  private final NotificationRepository notifications;
  private final DeliveryRepository deliveries;
  private final RecipientDirectoryService recipients;
  private final EmailProvider provider;
  private final NotificationProperties properties;
  private final Counter exhausted;

  public NotificationDeliveryService(
      NotificationRepository n,
      DeliveryRepository d,
      RecipientDirectoryService r,
      EmailProvider p,
      NotificationProperties props,
      MeterRegistry metrics) {
    notifications = n;
    deliveries = d;
    recipients = r;
    provider = p;
    properties = props;
    exhausted = Counter.builder("notification_delivery_exhausted_total").register(metrics);
  }

  @Scheduled(fixedDelayString = "${notification.delivery-poll-ms:5000}")
  public void deliverPending() {
    notifications
        .findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING)
        .forEach(
            n -> {
              try {
                deliver(n.getId());
              } catch (Exception ex) {
                log.error("Unexpected delivery worker error. notificationId={}", n.getId(), ex);
              }
            });
  }

  @Transactional
  public void deliver(UUID notificationId) {
    Notification n = notifications.findById(notificationId).orElseThrow();
    if (n.getStatus() != NotificationStatus.PENDING) return;
    NotificationDelivery previous =
        deliveries.findTopByNotificationIdOrderByAttemptCountDesc(n.getId()).orElse(null);
    if (previous != null
        && previous.getNextAttemptAt() != null
        && previous.getNextAttemptAt().isAfter(Instant.now())) return;
    int attempt = previous == null ? 1 : previous.getAttemptCount() + 1;
    NotificationDelivery d = new NotificationDelivery();
    d.setNotificationId(n.getId());
    d.setAttemptCount(attempt);
    d.setProvider(properties.getProvider());
    d.setStatus(DeliveryStatus.PENDING);
    Optional<String> email = recipients.findActiveEmail(n.getRecipientUserId());
    if (email.isEmpty()) {
      d.setStatus(DeliveryStatus.FAILED);
      d.setLastError("Recipient email unavailable from identity directory");
      retryOrFail(n, d);
      deliveries.save(d);
      return;
    }
    try {
      d.setProviderMessageId(provider.send(email.get(), n));
      d.setStatus(DeliveryStatus.SENT);
      n.setStatus(NotificationStatus.SENT);
      n.setSentAt(Instant.now());
      deliveries.save(d);
      notifications.save(n);
    } catch (Exception ex) {
      d.setStatus(DeliveryStatus.FAILED);
      d.setLastError(safeError(ex));
      retryOrFail(n, d);
      deliveries.save(d);
    }
  }

  private void retryOrFail(Notification n, NotificationDelivery d) {
    if (d.getAttemptCount() >= properties.getMaxAttempts()) {
      n.setStatus(NotificationStatus.FAILED);
      notifications.save(n);
      exhausted.increment();
      log.error(
          "Notification delivery exhausted. notificationId={}, attempts={}",
          n.getId(),
          d.getAttemptCount());
      return;
    }
    long base = properties.getRetryBaseDelay().toMillis();
    long cap = Math.min(base * (1L << (d.getAttemptCount() - 1)), Duration.ofHours(1).toMillis());
    d.setNextAttemptAt(
        Instant.now()
            .plusMillis(ThreadLocalRandom.current().nextLong(Math.max(1, cap / 2), cap + 1)));
  }

  private String safeError(Exception ex) {
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    return message.length() > 1000 ? message.substring(0, 1000) : message;
  }
}
