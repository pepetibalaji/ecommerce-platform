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

        /*
         * Local and automated test runs use the deterministic sandbox provider.
         * Deployments that provide Stripe test-mode credentials can explicitly
         * select STRIPE through Config Server configuration.
         */
        @NotNull(message = "Active payment provider is required")
        private PaymentProvider active = PaymentProvider.SANDBOX;

        @NotBlank(message = "Payment provider mode is required")
        private String mode = "test";

        @Positive(message = "Provider refund timeout must be greater than zero")
        private long refundTimeoutMs = 5000;

        @Valid
        @NotNull(message = "Sandbox payment provider configuration is required")
        private Sandbox sandbox = new Sandbox();

        @Valid
        @NotNull(message = "Stripe payment provider configuration is required")
        private Stripe stripe = new Stripe();

        @Valid
        @NotNull(message = "Razorpay payment provider configuration is required")
        private Razorpay razorpay = new Razorpay();

        @AssertTrue(message = "Active payment provider must be enabled")
        public boolean isActiveProviderEnabled() {
            if (active == PaymentProvider.STRIPE) {
                return stripe.enabled;
            }

            if (active == PaymentProvider.SANDBOX) {
                return sandbox.enabled;
            }

            if (active == PaymentProvider.RAZORPAY) {
                return razorpay.enabled;
            }

            return false;
        }

        @AssertTrue(message = "Stripe API key and webhook secret are required when Stripe is active")
        public boolean isStripeConfigurationValidWhenEnabled() {
            if (active != PaymentProvider.STRIPE || !stripe.enabled) {
                return true;
            }

            return hasText(stripe.apiKey)
                    && hasText(stripe.webhookSecret);
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

        /** Local-only secret used by deterministic sandbox webhook fixtures. */
        private String webhookSecret = "sandbox-test-signature";
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
        private String successUrl = "http://localhost:3000/payments/success?orderId={ORDER_ID}&paymentId={PAYMENT_ID}";

        @NotBlank(message = "Checkout cancel URL is required")
        private String cancelUrl = "http://localhost:3000/payments/cancel?orderId={ORDER_ID}&paymentId={PAYMENT_ID}";
    }
}
