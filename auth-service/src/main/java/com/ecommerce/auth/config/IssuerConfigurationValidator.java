package com.ecommerce.auth.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails closed when a non-local deployment is configured with an unstable issuer. */
@Component
public class IssuerConfigurationValidator {
  private final AuthorizationServerProperties properties;
  private final Environment environment;

  public IssuerConfigurationValidator(AuthorizationServerProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void validate() {
    if (environment.matchesProfiles("dev", "test")) {
      return;
    }
    try {
      URI issuer = URI.create(properties.getIssuer());
      if (!"https".equalsIgnoreCase(issuer.getScheme())
          || issuer.getHost() == null
          || "localhost".equalsIgnoreCase(issuer.getHost())
          || issuer.getHost().startsWith("127.")) {
        throw new IllegalStateException(
            "auth.authorization-server.issuer must be a stable public HTTPS URL outside dev/test");
      }
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("auth.authorization-server.issuer must be a valid HTTPS URL", exception);
    }
  }
}
