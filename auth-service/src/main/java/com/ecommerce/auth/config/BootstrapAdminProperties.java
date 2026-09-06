package com.ecommerce.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials for the one-time, operator-controlled first administrator bootstrap. */
@ConfigurationProperties(prefix = "auth.bootstrap-admin")
public class BootstrapAdminProperties {
  private String email;
  private String password;

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
}
