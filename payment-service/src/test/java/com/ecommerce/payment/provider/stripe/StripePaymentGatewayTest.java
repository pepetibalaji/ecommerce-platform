package com.ecommerce.payment.provider.stripe;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.provider.model.ProviderPaymentStatus;
import com.ecommerce.payment.exception.PaymentConfirmationUnavailableException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripePaymentGatewayTest {
    private StripePaymentGateway gateway() {
        var settings = new PaymentProviderProperties();
        settings.getProvider().getStripe().setEnabled(true);
        settings.getProvider().getStripe().setApiKey("sk_test_fixture_only");
        settings.getProvider().getStripe().setWebhookSecret("whsec_fixture_only");
        return new StripePaymentGateway(settings);
    }
    private Session session(String paymentStatus, String status) {
        var session = new Session();
        session.setId("cs_test_saved"); session.setPaymentStatus(paymentStatus); session.setStatus(status);
        session.setPaymentIntent("pi_test_saved"); return session;
    }
    @Test void retrievesSavedSessionAndOnlyConfirmsPaid() throws Exception {
        try (var stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(eq("cs_test_saved"), any(RequestOptions.class)))
                    .thenReturn(session("paid", "complete"), session("unpaid", "open"), session("unpaid", "complete"), session("unpaid", "expired"));
            assertThat(gateway().getPaymentStatus("cs_test_saved").getStatus()).isEqualTo(ProviderPaymentStatus.SUCCESS);
            assertThat(gateway().getPaymentStatus("cs_test_saved").getStatus()).isEqualTo(ProviderPaymentStatus.IGNORED);
            assertThat(gateway().getPaymentStatus("cs_test_saved").getStatus()).isEqualTo(ProviderPaymentStatus.PROCESSING);
            assertThat(gateway().getPaymentStatus("cs_test_saved").getStatus()).isEqualTo(ProviderPaymentStatus.CANCELLED);
        }
    }
    @Test void providerOutageDoesNotClaimFailureOrSuccess() throws Exception {
        try (var stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(eq("cs_test_saved"), any(RequestOptions.class)))
                    .thenThrow(mock(com.stripe.exception.StripeException.class));
            assertThatThrownBy(() -> gateway().getPaymentStatus("cs_test_saved")).isInstanceOf(PaymentConfirmationUnavailableException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"checkout.session.completed", "checkout.session.async_payment_succeeded"})
    void verifiesSignedImmediateAndDelayedSuccess(String type) throws Exception {
        String payload = payload(type, com.stripe.Stripe.API_VERSION);
        var event = gateway().parseWebhookEvent(payload, signature(payload));
        assertThat(event.getStatus()).isEqualTo(ProviderPaymentStatus.SUCCESS);
        assertThat(event.getProviderSessionId()).isEqualTo("cs_test_saved");
    }
    @Test void badSignatureCannotConfirmPayment() {
        assertThatThrownBy(() -> gateway().parseWebhookEvent(payload("checkout.session.completed", com.stripe.Stripe.API_VERSION), "t=1,v1=invalid"))
                .isInstanceOf(com.ecommerce.common.exception.BadRequestException.class);
    }
    @Test void mismatchedWebhookApiVersionUsesAuthenticatedLookup() throws Exception {
        try (var stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve(eq("cs_test_saved"), any(RequestOptions.class))).thenReturn(session("paid", "complete"));
            String payload = payload("checkout.session.completed", "2020-08-27");
            assertThat(gateway().parseWebhookEvent(payload, signature(payload)).getStatus()).isEqualTo(ProviderPaymentStatus.SUCCESS);
            stripe.verify(() -> Session.retrieve(eq("cs_test_saved"), any(RequestOptions.class)));
        }
    }
    private String payload(String type, String version) {
        return "{\"id\":\"evt_test\",\"object\":\"event\",\"api_version\":\"" + version + "\",\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"id\":\"cs_test_saved\",\"object\":\"checkout.session\",\"payment_status\":\"paid\",\"status\":\"complete\",\"payment_intent\":\"pi_test_saved\"}}}";
    }
    private String signature(String payload) throws Exception {
        long timestamp = java.time.Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_fixture_only".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
