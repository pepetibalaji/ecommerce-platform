package com.ecommerce.notification.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails fast in stage/prod if Auth action delivery cannot safely send transactional email. */
@Component
public class AuthActionDeliveryDeploymentValidator {
  private final Environment environment;
  private final AuthActionDeliveryProperties properties;

  public AuthActionDeliveryDeploymentValidator(
      Environment environment, AuthActionDeliveryProperties properties) {
    this.environment = environment;
    this.properties = properties;
  }

  @PostConstruct
  void validateStageAndProduction() {
    boolean deploymentProfile =
        Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "stage".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile));
    if (deploymentProfile) {
      properties.validateForDeployment();
    }
  }
}
