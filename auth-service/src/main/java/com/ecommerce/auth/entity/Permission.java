package com.ecommerce.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "permissions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Permission {
  @Id private UUID id;
  @Column(nullable = false, unique = true) private String code;
  private String description;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
}
