package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get my profile")
    public UserProfileResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return userService.getMe(currentUserId(jwt));
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile")
    public UserProfileResponse updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        return userService.updateMe(currentUserId(jwt), request);
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete my profile")
    public void deleteMe(@AuthenticationPrincipal Jwt jwt) {
        userService.deleteMe(currentUserId(jwt));
    }

    private UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}