package com.ecommerce.product.outbox;

import com.ecommerce.common.events.product.ProductLifecycleEvent;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("product_outbox") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductOutboxEvent {
  @Id private UUID id;
  private UUID productId;
  private UUID sellerId;
  private String type;
  private Instant occurredAt;
  private ProductLifecycleEvent payload;
  @Builder.Default private int attempts = 0;
  @Builder.Default private Status status = Status.PENDING;
  private Instant leaseUntil;
  private UUID leaseToken;
  private Instant nextAttemptAt;
  private Instant publishedAt;
  private String lastError;
  public enum Status { PENDING, PROCESSING, PUBLISHED, DEAD }
}
