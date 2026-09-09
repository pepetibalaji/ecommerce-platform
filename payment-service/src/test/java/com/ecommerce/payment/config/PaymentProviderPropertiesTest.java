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

    @Test
    void defaultsCheckoutReturnsToTheFrontendPaymentStatusScreen() {
        PaymentProviderProperties properties = new PaymentProviderProperties();

        assertThat(properties.getCheckout().getSuccessUrl())
                .isEqualTo("http://localhost:5173/payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}");
        assertThat(properties.getCheckout().getCancelUrl())
                .isEqualTo("http://localhost:5173/payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}");
    }
}
