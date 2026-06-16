package com.ecommerce.auth.dto;

import com.ecommerce.auth.entity.enums.Role;
import com.ecommerce.auth.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private UUID id;
    private String name;
    private String email;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}