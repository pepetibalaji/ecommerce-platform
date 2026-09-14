package com.ecommerce.payment.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class PaymentInfrastructureSafetyTest {
    private MockEnvironment environment(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test void explicitDevelopmentProfilesPermitLocalInfrastructure() {
        for (String profile : new String[]{"dev", "local", "test"}) {
            assertThatCode(() -> new PaymentInfrastructureSafety(environment(profile)).validate()).doesNotThrowAnyException();
        }
        assertThatCode(() -> new PaymentInfrastructureSafety(environment("dev", "test")).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "stage", "production", "unknown"})
    void nonDevelopmentProfilesRequireAuthenticatedEncryptedKafka(String profile) {
        var environment = environment(profile).withProperty("payment.order-lookup.secret", "x".repeat(32));
        assertThatThrownBy(() -> new PaymentInfrastructureSafety(environment).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SASL_SSL");
        environment.setProperty("spring.kafka.properties.security.protocol", "SASL_PLAINTEXT");
        assertThatThrownBy(() -> new PaymentInfrastructureSafety(environment).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SASL_SSL");
    }

    @Test void missingProfileAndMixedDevelopmentProductionCannotBypassChecks() {
        assertThatThrownBy(() -> new PaymentInfrastructureSafety(environment()).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentInfrastructureSafety(environment("dev", "prod")).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void trustedOrderSecretMustBeConfiguredInProduction() {
        var environment = environment("prod").withProperty("spring.kafka.properties.security.protocol", "SASL_SSL");
        for (String secret : new String[]{"", "short", "x".repeat(31), " ".repeat(32)}) {
            environment.setProperty("payment.order-lookup.secret", secret);
            assertThatThrownBy(() -> new PaymentInfrastructureSafety(environment).validate())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
        }
        environment.setProperty("payment.order-lookup.secret", "x".repeat(32));
        assertThatCode(() -> new PaymentInfrastructureSafety(environment).validate()).doesNotThrowAnyException();
    }
}
