package com.ecommerce.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;

class InternalServiceAuthorizerTest {

  @Test
  void acceptsOnlyMatchingConfiguredCredential() {
    var authorizer = new InternalServiceAuthorizer("test-service-secret", new MockEnvironment());
    assertThatCode(() -> authorizer.requireAuthorized("test-service-secret")).doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.requireAuthorized("incorrect-secret"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> authorizer.requireAuthorized(null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "${AUTH_INTERNAL_SERVICE_TOKEN:}"})
  void invalidConfigurationNeverBecomesAUsableCredential(String secret) {
    var authorizer = new InternalServiceAuthorizer(secret, new MockEnvironment());
    assertThatThrownBy(() -> authorizer.requireAuthorized(secret))
        .isInstanceOf(AccessDeniedException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"stage", "prod"})
  void deployedProfilesRefuseToStartWithoutResolvedSecret(String profile) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    assertThatThrownBy(() -> new InternalServiceAuthorizer("", environment))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new InternalServiceAuthorizer("${AUTH_INTERNAL_SERVICE_TOKEN:}", environment))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runtimeSecretOverridesConfigServerPlaceholder() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("AUTH_INTERNAL_SERVICE_TOKEN", "runtime-test-secret");
    environment.setActiveProfiles("prod");
    var authorizer = new InternalServiceAuthorizer("${AUTH_INTERNAL_SERVICE_TOKEN:}", environment);
    assertThatCode(() -> authorizer.requireAuthorized("runtime-test-secret")).doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.requireAuthorized("${AUTH_INTERNAL_SERVICE_TOKEN:}"))
        .isInstanceOf(AccessDeniedException.class);
  }
}
