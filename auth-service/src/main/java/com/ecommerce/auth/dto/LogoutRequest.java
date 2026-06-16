package com.ecommerce.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LogoutRequest {

    @Size(max = 2048, message = "Refresh token is too long")
    private String refreshToken;
}