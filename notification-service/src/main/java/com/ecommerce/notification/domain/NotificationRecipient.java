package com.ecommerce.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_recipients")
@Getter
@Setter
@NoArgsConstructor
public class NotificationRecipient {
  @Id private UUID userId;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private Instant updatedAt;
}
