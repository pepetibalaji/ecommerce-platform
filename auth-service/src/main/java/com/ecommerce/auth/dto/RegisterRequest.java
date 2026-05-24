package com.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email format")

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")

    @Size(
            min = 8,
            message = "Password must be at least 8 characters"
    )
    private String password;
}