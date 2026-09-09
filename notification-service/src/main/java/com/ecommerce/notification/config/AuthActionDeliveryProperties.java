package com.ecommerce.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Settings used only for Auth identity-action emails. The internal token must be supplied by the
 * deployment secret manager; it is deliberately not a Kafka event field.
 */
@ConfigurationProperties("notification.auth-action-delivery")
public class AuthActionDeliveryProperties {
  private String authBaseUrl = "http://auth-service:8081";
  private String internalServiceToken;
  private String verificationUrl = "http://localhost:5173/verify-email";
  private String passwordResetUrl = "http://localhost:5173/reset-password";
  private String emailChangeUrl = "http://localhost:5173/confirm-email-change";
  private boolean authBaseUrlConfigured;
  private boolean internalServiceTokenConfigured;
  private boolean verificationUrlConfigured;
  private boolean passwordResetUrlConfigured;
  private boolean emailChangeUrlConfigured;

  public String getAuthBaseUrl() {
    return authBaseUrl;
  }

  public void setAuthBaseUrl(String authBaseUrl) {
    this.authBaseUrl = authBaseUrl;
    this.authBaseUrlConfigured = true;
  }

  public String getInternalServiceToken() {
    return internalServiceToken;
  }

  public void setInternalServiceToken(String internalServiceToken) {
    this.internalServiceToken = internalServiceToken;
    this.internalServiceTokenConfigured = true;
  }

  public String getVerificationUrl() {
    return verificationUrl;
  }

  public void setVerificationUrl(String verificationUrl) {
    this.verificationUrl = verificationUrl;
    this.verificationUrlConfigured = true;
  }

  public String getPasswordResetUrl() {
    return passwordResetUrl;
  }

  public void setPasswordResetUrl(String passwordResetUrl) {
    this.passwordResetUrl = passwordResetUrl;
    this.passwordResetUrlConfigured = true;
  }

  public String getEmailChangeUrl() {
    return emailChangeUrl;
  }

  public void setEmailChangeUrl(String emailChangeUrl) {
    this.emailChangeUrl = emailChangeUrl;
    this.emailChangeUrlConfigured = true;
  }

  public void requireInternalServiceToken() {
    if (!StringUtils.hasText(internalServiceToken)) {
      throw new IllegalStateException(
          "notification.auth-action-delivery.internal-service-token must be configured");
    }
  }

  public void validateForDeployment() {
    requireConfigured(authBaseUrlConfigured, "auth-base-url");
    requireConfigured(internalServiceTokenConfigured, "internal-service-token");
    requireConfigured(verificationUrlConfigured, "verification-url");
    requireConfigured(passwordResetUrlConfigured, "password-reset-url");
    requireConfigured(emailChangeUrlConfigured, "email-change-url");
    requireText(authBaseUrl, "auth-base-url");
    requireInternalServiceToken();
    requireText(verificationUrl, "verification-url");
    requireText(passwordResetUrl, "password-reset-url");
    requireText(emailChangeUrl, "email-change-url");
  }

  public String actionUrl(String notificationType, String token) {
    String baseUrl =
        switch (notificationType) {
          case "EMAIL_VERIFICATION" -> verificationUrl;
          case "PASSWORD_RESET" -> passwordResetUrl;
          case "EMAIL_CHANGE_CONFIRMATION" -> emailChangeUrl;
          default -> throw new IllegalArgumentException("Unsupported auth action notification type");
        };
    if (!StringUtils.hasText(baseUrl)) {
      throw new IllegalStateException("Auth action email URL is not configured");
    }
    return UriComponentsBuilder.fromUriString(baseUrl)
        .queryParam("token", token)
        .build()
        .encode()
        .toUriString();
  }

  private void requireText(String value, String propertyName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(
          "notification.auth-action-delivery." + propertyName + " must be configured");
    }
  }

  private void requireConfigured(boolean configured, String propertyName) {
    if (!configured) {
      throw new IllegalStateException(
          "notification.auth-action-delivery."
              + propertyName
              + " must be explicitly configured for stage/prod");
    }
  }
}
