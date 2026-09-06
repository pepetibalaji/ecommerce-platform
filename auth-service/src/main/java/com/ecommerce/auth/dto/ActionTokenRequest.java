package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionTokenRequest {
  @NotBlank private String token;
}
