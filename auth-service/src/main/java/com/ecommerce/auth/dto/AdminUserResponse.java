package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.enums.UserStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

  private UUID id;
  private String name;
  private String email;
  private List<String> roles;
  private UserStatus status;
  private Instant createdAt;
  private Instant updatedAt;
}
