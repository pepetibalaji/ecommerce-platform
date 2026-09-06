package com.ecommerce.notification.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthActionDeliveryPropertiesTest {
  @Test
  void stageAndProdRequireExplicitValuesRatherThanSilentlyUsingDevDefaults() {
    assertThatThrownBy(() -> new AuthActionDeliveryProperties().validateForDeployment())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be explicitly configured");
  }

  @Test
  void acceptsAllExplicitStageOrProductionSettings() {
    AuthActionDeliveryProperties properties = new AuthActionDeliveryProperties();
    properties.setAuthBaseUrl("https://auth.internal.example.test");
    properties.setInternalServiceToken("secret-manager-injected");
    properties.setVerificationUrl("https://shop.example.test/verify-email");
    properties.setPasswordResetUrl("https://shop.example.test/reset-password");
    properties.setEmailChangeUrl("https://shop.example.test/confirm-email-change");

    assertThatCode(properties::validateForDeployment).doesNotThrowAnyException();
  }
}
