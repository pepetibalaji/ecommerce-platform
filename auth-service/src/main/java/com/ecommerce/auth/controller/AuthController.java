package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.ActionTokenRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.ForgotPasswordRequest;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LogoutRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.ResendVerificationRequest;
import com.ecommerce.auth.dto.ResetPasswordRequest;
import com.ecommerce.auth.service.ActionTokenService;
import com.ecommerce.auth.service.AuditRequestContext;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

  private final AuthService authService;
  private final RegistrationService registrationService;
  private final ActionTokenService actionTokenService;

  @PostMapping("/register")
  @Operation(summary = "Register a new user")
  public ResponseEntity<Void> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    registrationService.register(request, AuditRequestContext.from(servletRequest));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/verification/confirm")
  public ResponseEntity<Void> confirmVerification(
      @Valid @RequestBody ActionTokenRequest request, HttpServletRequest servletRequest) {
    actionTokenService.confirmVerification(request, AuditRequestContext.from(servletRequest));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/verification/resend")
  public ResponseEntity<Void> resendVerification(
      @Valid @RequestBody ResendVerificationRequest request, HttpServletRequest servletRequest) {
    actionTokenService.resendVerification(request, AuditRequestContext.from(servletRequest));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password/forgot")
  public ResponseEntity<Void> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
    actionTokenService.requestPasswordReset(request, AuditRequestContext.from(servletRequest));
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password/reset")
  public ResponseEntity<Void> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
    actionTokenService.resetPassword(request, AuditRequestContext.from(servletRequest));
    return ResponseEntity.noContent().build();
  }

  /** Confirms a one-time email-change token without relying on an existing browser session. */
  @PostMapping("/email-change/confirm")
  public ResponseEntity<Void> confirmEmailChange(
      @Valid @RequestBody ActionTokenRequest request, HttpServletRequest servletRequest) {
    actionTokenService.confirmEmailChange(request, AuditRequestContext.from(servletRequest));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  @Operation(summary = "Login and issue tokens")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    return authService.login(request, AuditRequestContext.from(servletRequest));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh access token")
  public AuthResponse refresh(
      @Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
    return authService.refresh(request, AuditRequestContext.from(servletRequest));
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout current session")
  public void logout(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody(required = false) LogoutRequest request,
      HttpServletRequest servletRequest) {
    String refreshToken = request != null ? request.getRefreshToken() : null;
    authService.logout(jwt, refreshToken, AuditRequestContext.from(servletRequest));
  }
}
