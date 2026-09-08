package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.ActionTokenRequest;
import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.EmailChangeRequest;
import com.ecommerce.auth.dto.SessionResponse;
import com.ecommerce.auth.dto.UpdateMeRequest;
import com.ecommerce.auth.dto.UpdateStatusRequest;
import com.ecommerce.auth.dto.UserProfileResponse;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.service.AccountService;
import com.ecommerce.auth.service.ActionTokenService;
import com.ecommerce.auth.service.AuditRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

  private final AccountService userService;
  private final ActionTokenService actionTokenService;

  public UserController(AccountService userService, ActionTokenService actionTokenService) {
    this.userService = userService;
    this.actionTokenService = actionTokenService;
  }

  @PostMapping("/me/password")
  public void changePassword(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest servletRequest) {
    userService.password(currentUserId(jwt), request, AuditRequestContext.from(servletRequest));
  }

  @PostMapping("/me/email-change")
  public void requestEmailChange(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EmailChangeRequest request,
      HttpServletRequest servletRequest) {
    actionTokenService.requestEmailChange(
        currentUserId(jwt), request, AuditRequestContext.from(servletRequest));
  }

  @GetMapping("/me")
  @Operation(summary = "Get my profile")
  public UserProfileResponse getMe(@AuthenticationPrincipal Jwt jwt) {
    return userService.me(currentUserId(jwt));
  }

  @GetMapping("/me/sessions")
  public List<SessionResponse> getSessions(
      @AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
    return userService.sessions(currentUserId(jwt), AuditRequestContext.from(servletRequest));
  }

  @DeleteMapping("/me/sessions/{sessionId}")
  public void revokeSession(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID sessionId,
      HttpServletRequest servletRequest) {
    userService.revokeSession(
        currentUserId(jwt), sessionId, AuditRequestContext.from(servletRequest));
  }

  @PutMapping("/me")
  @Operation(summary = "Update my profile")
  public UserProfileResponse updateMe(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody UpdateMeRequest request,
      HttpServletRequest servletRequest) {
    return userService.update(
        currentUserId(jwt), request, AuditRequestContext.from(servletRequest));
  }

  @DeleteMapping("/me")
  @Operation(summary = "Delete my profile")
  public void deleteMe(@AuthenticationPrincipal Jwt jwt, HttpServletRequest servletRequest) {
    UUID userId = currentUserId(jwt);
    UpdateStatusRequest request = new UpdateStatusRequest();
    request.setStatus(UserStatus.DELETED);
    userService.status(userId, request, userId, AuditRequestContext.from(servletRequest));
  }

  private UUID currentUserId(Jwt jwt) {
    return UUID.fromString(jwt.getClaimAsString("userId"));
  }
}
