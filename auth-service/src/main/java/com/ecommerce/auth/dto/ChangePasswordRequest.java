package com.ecommerce.auth.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class ChangePasswordRequest { @NotBlank private String currentPassword; @NotBlank @Size(min=12,max=128) private String newPassword; }
