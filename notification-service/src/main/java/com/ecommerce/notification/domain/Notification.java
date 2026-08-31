package com.ecommerce.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_event_recipient_channel",
            columnNames = {"event_id", "recipient_user_id", "channel"}))
@Getter
@Setter
@NoArgsConstructor
public class Notification {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Channel channel;

  @Column(nullable = false, length = 80)
  private String type;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant sentAt;

  @PrePersist
  void created() {
    if (id == null) id = UUID.randomUUID();
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void updated() {
    updatedAt = Instant.now();
  }
}
