package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

  @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
  @NotBlank(message = "Name is required")
  private String name;

  @Email(message = "Invalid email format")
  @NotBlank(message = "Email is required")
  @Size(max = 255, message = "Email must be at most 255 characters")
  private String email;

  @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
  @NotBlank(message = "Password is required")
  private String password;

  /**
   * Retained only to give legacy clients a clear validation error. Public registration never
   * creates privileged accounts.
   */
  private Role role = Role.CUSTOMER;
}
