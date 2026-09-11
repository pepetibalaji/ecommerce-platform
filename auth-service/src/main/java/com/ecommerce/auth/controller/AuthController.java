package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.ActionTokenRequest;
import com.ecommerce.auth.dto.AuthResponse;
import com.ecommerce.auth.dto.ForgotPasswordRequest;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RegisterRequest;
import com.ecommerce.auth.dto.ResendVerificationRequest;
import com.ecommerce.auth.dto.ResetPasswordRequest;
import com.ecommerce.auth.service.ActionTokenService;
import com.ecommerce.auth.service.AuditRequestContext;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.auth.service.BrowserRefreshCookieService;
import com.ecommerce.auth.service.RegistrationService;
import com.ecommerce.common.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
  private final BrowserRefreshCookieService refreshCookies;

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
  @Operation(summary = "Login and issue browser session")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    return issueBrowserSession(authService.login(request, AuditRequestContext.from(servletRequest)), servletResponse);
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate browser refresh cookie and issue access token")
  public ResponseEntity<AuthResponse> refresh(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    RefreshRequest request = new RefreshRequest();
    request.setRefreshToken(refreshCookies.read(servletRequest)
        .orElseThrow(() -> new UnauthorizedException("Refresh session is missing")));
    return issueBrowserSession(authService.refresh(request, AuditRequestContext.from(servletRequest)), servletResponse);
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout current session")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal Jwt jwt,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    try {
      authService.logout(jwt, refreshCookies.read(servletRequest).orElse(null), AuditRequestContext.from(servletRequest));
    } finally {
      refreshCookies.clear(servletResponse);
    }
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<AuthResponse> issueBrowserSession(AuthResponse issued, HttpServletResponse response) {
    refreshCookies.write(response, issued.getRefreshToken());
    return ResponseEntity.ok(AuthResponse.builder()
        .accessToken(issued.getAccessToken())
        .tokenType(issued.getTokenType())
        .expiresInSeconds(issued.getExpiresInSeconds())
        .user(issued.getUser())
        .build());
  }
}
