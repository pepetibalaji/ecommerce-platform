package com.ecommerce.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "notification_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class NotificationDelivery {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID notificationId;

  @Column(nullable = false)
  private int attemptCount;

  @Column(nullable = false, length = 50)
  private String provider;

  private String providerMessageId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DeliveryStatus status;

  @Column(columnDefinition = "text")
  private String lastError;

  private Instant nextAttemptAt;

  @Column(nullable = false)
  private Instant createdAt;

  @PrePersist
  void created() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
