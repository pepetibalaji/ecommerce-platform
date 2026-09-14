package com.ecommerce.payment.config;

import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.exception.PaymentApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class CheckoutUrlPolicyTest {
    PaymentProviderProperties properties = new PaymentProviderProperties();
    MockEnvironment environment = new MockEnvironment();
    CheckoutUrlPolicy policy = new CheckoutUrlPolicy(properties, environment);

    @ParameterizedTest
    @ValueSource(strings = {"http://checkout.stripe.com/session", "https://checkout.stripe.com.evil.test/session",
            "https://checkout.stripe.com@evil.test/session", "https://checkout.stripe.com:444/session",
            "//checkout.stripe.com/session", "javascript:alert(1)", "https://evil.test", "https://checkout.stripe.com/s#fragment"})
    void refusesUnapprovedRedirects(String url) {
        assertThatThrownBy(() -> policy.validateCheckoutUrl(PaymentProvider.STRIPE, url))
                .isInstanceOf(PaymentApiException.class);
    }

    @Test void permitsApprovedHttpsProviderAndExactFrontendOrigin() {
        policy.validateCheckoutUrl(PaymentProvider.STRIPE, "https://checkout.stripe.com/c/pay/cs_test");
        properties.getCheckout().setFrontendOrigins(Set.of("https://shop.example"));
        policy.validateReturnUrl("https://shop.example/payment/return?orderId=1&paymentId=2");
        assertThatThrownBy(() -> policy.validateReturnUrl("https://shop.example/public/payments/success"))
                .isInstanceOf(PaymentApiException.class);
    }

    @Test void sandboxAndHttpAreOnlyAllowedInExplicitDevelopmentProfiles() {
        assertThatThrownBy(() -> policy.validateCheckoutUrl(PaymentProvider.SANDBOX, "http://localhost:3001/mock-checkout"))
                .isInstanceOf(PaymentApiException.class);
        environment.setActiveProfiles("dev");
        policy.validateCheckoutUrl(PaymentProvider.SANDBOX, "http://localhost:3001/mock-checkout");
        environment.setActiveProfiles("dev", "prod");
        assertThatThrownBy(() -> policy.validateCheckoutUrl(PaymentProvider.SANDBOX, "http://localhost:3001/mock-checkout"))
                .isInstanceOf(PaymentApiException.class);
    }

    @Test void providerSafetyRejectsRazorpayAndUnverifiedProductionStripe() {
        environment.setActiveProfiles("prod");
        properties.getProvider().getSandbox().setEnabled(false);
        properties.getProvider().setActive(PaymentProvider.STRIPE);
        properties.getProvider().getStripe().setEnabled(true);
        assertThatThrownBy(() -> new PaymentProviderSafety(properties, policy).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("staging");
        environment.setActiveProfiles("dev");
        properties.getProvider().getRazorpay().setEnabled(true);
        assertThatThrownBy(() -> new PaymentProviderSafety(properties, policy).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Razorpay");
    }
}
