package com.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(max = 100, message = "Password must be at most 100 characters")
    private String password;
}