package com.ecommerce.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "notification_processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {
  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false)
  private Instant processedAt;

  public ProcessedEvent(UUID id) {
    eventId = id;
    processedAt = Instant.now();
  }
}
