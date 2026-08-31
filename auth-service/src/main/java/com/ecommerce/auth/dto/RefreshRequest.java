package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshRequest {

  @NotBlank(message = "Refresh token is required")
  @Size(max = 2048, message = "Refresh token is too long")
  private String refreshToken;
}
