package com.ecommerce.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Temporary service-to-service boundary for internal delivery-token calls.
 * Deployments must inject a high-entropy value from the secret manager.
 */
@Component
public class InternalServiceAuthorizer {
  private final byte[] expectedToken;

  public InternalServiceAuthorizer(
      @Value("${auth.internal.service-token:}") String configuredServiceToken,
      Environment environment) {
    // Secrets injected by a platform secret manager (or the local ignored .env) must win over
    // a Config Server placeholder. Config Server intentionally does not resolve a client's
    // process environment, so using only auth.internal.service-token can leave the literal
    // ${AUTH_INTERNAL_SERVICE_TOKEN:} as the effective value.
    String runtimeServiceToken = environment.getProperty("AUTH_INTERNAL_SERVICE_TOKEN");
    String serviceToken =
        runtimeServiceToken != null && !runtimeServiceToken.isBlank()
            ? runtimeServiceToken
            : configuredServiceToken;
    if ((environment.matchesProfiles("stage", "prod"))
        && (serviceToken == null || serviceToken.isBlank())) {
      throw new IllegalStateException(
          "auth.internal.service-token must be supplied from the secret manager in stage/prod");
    }
    this.expectedToken = serviceToken == null ? new byte[0] : serviceToken.getBytes(StandardCharsets.UTF_8);
  }

  public void requireAuthorized(String presentedToken) {
    byte[] presented = presentedToken == null ? new byte[0] : presentedToken.getBytes(StandardCharsets.UTF_8);
    // Evaluate the comparison even for a missing configured secret, avoiding a fast token-match path.
    boolean matches = MessageDigest.isEqual(expectedToken, presented);
    if (expectedToken.length == 0 || !matches) {
      throw new AccessDeniedException("Internal service authentication failed");
    }
  }
}
