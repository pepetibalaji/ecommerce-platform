package com.ecommerce.auth.dto;
import com.ecommerce.auth.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class UpdateStatusRequest { @NotNull private UserStatus status; }
