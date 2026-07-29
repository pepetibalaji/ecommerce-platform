package com.ecommerce.payment.config;

import com.ecommerce.payment.enums.PaymentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderPropertiesTest {

    @Test
    void defaultsToEnabledSandboxInsteadOfCredentiallessStripe() {
        PaymentProviderProperties properties = new PaymentProviderProperties();

        assertThat(properties.getProvider().getActive()).isEqualTo(PaymentProvider.SANDBOX);
        assertThat(properties.getProvider().getSandbox().isEnabled()).isTrue();
        assertThat(properties.getProvider().getStripe().isEnabled()).isFalse();
        assertThat(properties.getProvider().isActiveProviderEnabled()).isTrue();
    }
}
