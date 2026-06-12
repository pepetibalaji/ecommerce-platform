package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.AdminUserResponse;
import com.ecommerce.auth.dto.ChangeRoleRequest;
import com.ecommerce.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Admin user management APIs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all users")
    public Page<AdminUserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userService.getAllUsers(PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id")
    public AdminUserResponse getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user")
    public void deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Change user role")
    public AdminUserResponse changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        return userService.changeRole(id, request);
    }
}