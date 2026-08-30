package com.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMeRequest {

  @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
  @NotBlank(message = "Name is required")
  private String name;
}
