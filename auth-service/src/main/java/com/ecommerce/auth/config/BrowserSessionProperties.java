package com.ecommerce.auth.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Browser-session policy. Refresh secrets are delivered only in an HTTP-only cookie. */
@Validated
@ConfigurationProperties(prefix = "auth.browser-session")
public class BrowserSessionProperties {

  @NotNull private Duration idleTimeout = Duration.ofMinutes(30);
  @NotNull private Duration absoluteTimeout = Duration.ofDays(7);
  @Valid private RefreshCookie refreshCookie = new RefreshCookie();

  public Duration getIdleTimeout() { return idleTimeout; }
  public void setIdleTimeout(Duration idleTimeout) { this.idleTimeout = idleTimeout; }
  public Duration getAbsoluteTimeout() { return absoluteTimeout; }
  public void setAbsoluteTimeout(Duration absoluteTimeout) { this.absoluteTimeout = absoluteTimeout; }
  public RefreshCookie getRefreshCookie() { return refreshCookie; }
  public void setRefreshCookie(RefreshCookie refreshCookie) { this.refreshCookie = refreshCookie; }

  @PostConstruct
  void validatePolicy() {
    if (idleTimeout.isNegative() || idleTimeout.isZero() || absoluteTimeout.isNegative() || absoluteTimeout.isZero()) {
      throw new IllegalStateException("Browser session timeouts must be positive");
    }
    if (idleTimeout.compareTo(absoluteTimeout) > 0) {
      throw new IllegalStateException("Browser idle timeout cannot exceed absolute timeout");
    }
    refreshCookie.validate();
  }

  public static class RefreshCookie {
    @NotBlank private String name = "pepekart_refresh";
    @NotBlank private String path = "/api/v1/auth";
    @NotBlank private String sameSite = "Lax";
    private boolean secure;
    @NotNull private Duration maxAge = Duration.ofDays(7);

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSameSite() { return sameSite; }
    public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public Duration getMaxAge() { return maxAge; }
    public void setMaxAge(Duration maxAge) { this.maxAge = maxAge; }

    private void validate() {
      if (maxAge.isNegative() || maxAge.isZero()) throw new IllegalStateException("Refresh cookie max age must be positive");
      if (!sameSite.equalsIgnoreCase("Lax") && !sameSite.equalsIgnoreCase("Strict") && !sameSite.equalsIgnoreCase("None")) {
        throw new IllegalStateException("Refresh cookie SameSite must be Lax, Strict, or None");
      }
      if (sameSite.equalsIgnoreCase("None") && !secure) {
        throw new IllegalStateException("SameSite=None refresh cookies must be Secure");
      }
      if (name.startsWith("__Host-") && (!secure || !"/".equals(path))) {
        throw new IllegalStateException("__Host- refresh cookies must be Secure and use Path=/");
      }
    }
  }
}
