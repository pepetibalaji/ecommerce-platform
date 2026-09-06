package com.ecommerce.auth.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External Authorization Server settings. Values belong in the environment-specific config
 * repository, not in application source.
 */
@ConfigurationProperties(prefix = "auth.authorization-server")
public class AuthorizationServerProperties {

  /**
   * Public issuer URL. This must be stable because resource servers validate the {@code iss}
   * claim against it.
   */
  private String issuer = "http://localhost:8081";

  /** Optional audiences to add to issued OAuth access tokens. */
  private Set<String> audiences = new LinkedHashSet<>();

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public Set<String> getAudiences() {
    return audiences;
  }

  public void setAudiences(Set<String> audiences) {
    this.audiences = audiences == null ? new LinkedHashSet<>() : new LinkedHashSet<>(audiences);
  }
}
