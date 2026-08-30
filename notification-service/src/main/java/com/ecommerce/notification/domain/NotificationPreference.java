package com.ecommerce.notification.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "notification_preferences",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_notification_preference",
            columnNames = {"user_id", "channel", "notification_type"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {
  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Channel channel;

  @Column(nullable = false)
  private String notificationType;

  @Column(nullable = false)
  private boolean enabled = true;
}
