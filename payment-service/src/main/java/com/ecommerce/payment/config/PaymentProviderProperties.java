package com.ecommerce.payment.config;

import com.ecommerce.payment.enums.PaymentProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "payment")
public class PaymentProviderProperties {

    @Valid
    @NotNull(message = "Payment provider configuration is required")
    private Provider provider = new Provider();

    @Valid
    @NotNull(message = "Payment checkout configuration is required")
    private Checkout checkout = new Checkout();

    @Getter
    @Setter
    public static class Provider {

        @NotNull(message = "Active payment provider is required")
        private PaymentProvider active = PaymentProvider.SANDBOX;

        @NotBlank(message = "Payment provider mode is required")
        private String mode = "sandbox";

        @Valid
        @NotNull(message = "Sandbox payment provider configuration is required")
        private Sandbox sandbox = new Sandbox();

        @Valid
        @NotNull(message = "Stripe payment provider configuration is required")
        private Stripe stripe = new Stripe();

        @Valid
        @NotNull(message = "Razorpay payment provider configuration is required")
        private Razorpay razorpay = new Razorpay();

        @AssertTrue(message = "Stripe API key and webhook secret are required when Stripe is active")
        public boolean isStripeConfigurationValidWhenEnabled() {
            if (active != PaymentProvider.STRIPE || !stripe.enabled) {
                return true;
            }

            return hasText(stripe.apiKey) && hasText(stripe.webhookSecret);
        }

        @AssertTrue(message = "Razorpay key id, key secret, and webhook secret are required when Razorpay is active")
        public boolean isRazorpayConfigurationValidWhenEnabled() {
            if (active != PaymentProvider.RAZORPAY || !razorpay.enabled) {
                return true;
            }

            return hasText(razorpay.keyId)
                    && hasText(razorpay.keySecret)
                    && hasText(razorpay.webhookSecret);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Sandbox {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Stripe {

        private boolean enabled = false;

        private String apiKey = "";

        private String webhookSecret = "";

        @Positive(message = "Stripe timeout must be greater than zero")
        private long timeoutMs = 5000;
    }

    @Getter
    @Setter
    public static class Razorpay {

        private boolean enabled = false;

        private String keyId = "";

        private String keySecret = "";

        private String webhookSecret = "";

        @Positive(message = "Razorpay timeout must be greater than zero")
        private long timeoutMs = 5000;
    }

    @Getter
    @Setter
    public static class Checkout {

        @NotBlank(message = "Checkout success URL is required")
        private String successUrl;

        @NotBlank(message = "Checkout cancel URL is required")
        private String cancelUrl;
    }
}