package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotNull(message = "Role is required")
    private Role role;
}