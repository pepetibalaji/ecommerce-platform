package com.ecommerce.payment.webhook;

import com.ecommerce.payment.config.PaymentProviderProperties;
import com.ecommerce.payment.provider.stripe.StripePaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentWebhookAdmissionFilterTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper json = new ObjectMapper();

    private PaymentWebhookAdmissionFilter filter(int maxBytes, int limit) {
        var filter = new PaymentWebhookAdmissionFilter(jdbc);
        ReflectionTestUtils.setField(filter, "maxBodyBytes", maxBytes);
        ReflectionTestUtils.setField(filter, "stripeRate", limit);
        return filter;
    }

    private MockHttpServletRequest request(byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/stripe");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    @Test void rejectsDeclaredOversizeBeforeReadingOrRateDatabase() throws Exception {
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        filter(8, 10).doFilter(request(new byte[9]), response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(json.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("WEBHOOK_PAYLOAD_TOO_LARGE");
        assertThat(json.readTree(response.getContentAsString()).path("retryable").asBoolean()).isFalse();
        assertThat(json.readTree(response.getContentAsString()).path("traceId").asText()).isNotBlank();
        verifyNoInteractions(jdbc, chain);
    }

    @Test void rejectsChunkedOversizeWithoutTrustingContentLength() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        var request = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/stripe") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContent(new byte[5000]);
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        filter(64, 10).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    @Test void preservesExactRawUtf8BytesWhitespaceAndSignatureForProviderVerification() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        String raw = " {\r\n \"id\":\"evt_raw\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION
                + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"id\":\"cs_raw\",\"object\":\"checkout.session\",\"payment_status\":\"paid\","
                + "\"metadata\":{\"description\":\"caf\u00e9\"}}}} \n";
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        String signature = signature(bytes);
        var properties = new PaymentProviderProperties();
        properties.getProvider().getStripe().setEnabled(true);
        properties.getProvider().getStripe().setApiKey("sk_test_fixture");
        properties.getProvider().getStripe().setWebhookSecret("whsec_fixture");
        var gateway = new StripePaymentGateway(properties);
        var request = request(bytes);
        request.addHeader("Stripe-Signature", signature);
        AtomicBoolean verified = new AtomicBoolean();
        filter(bytes.length, 10).doFilter(request, new MockHttpServletResponse(), (forwarded, response) -> {
            var http = (HttpServletRequest) forwarded;
            byte[] actual = http.getInputStream().readAllBytes();
            assertThat(actual).isEqualTo(bytes);
            assertThat(http.getHeader("Stripe-Signature")).isEqualTo(signature);
            gateway.verifyWebhookSignature(new String(actual, StandardCharsets.UTF_8), http.getHeader("Stripe-Signature"));
            assertThat(http.getReader().lines().reduce("", (left, right) -> left + right)).contains("caf\u00e9");
            verified.set(true);
        });
        assertThat(verified).isTrue();
    }

    @Test void sharedDatabaseLimitRejectsBeforeDownstreamProcessing() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(11);
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        filter(1024, 10).doFilter(request("{}".getBytes(StandardCharsets.UTF_8)), response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        var error = json.readTree(response.getContentAsString());
        assertThat(error.path("code").asText()).isEqualTo("WEBHOOK_RATE_LIMITED");
        assertThat(error.path("retryAfterSeconds").asInt()).isEqualTo(60);
        assertThat(error.path("retryable").asBoolean()).isTrue();
        verifyNoInteractions(chain);
    }

    @Test void rateDatabaseOutageFailsClosedWithSafeRetryableResponse() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret jdbc details"));
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter(1024, 10).doFilter(request(new byte[0]), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(response.getContentAsString()).contains("WEBHOOK_ADMISSION_UNAVAILABLE").doesNotContain("secret jdbc details");
        verifyNoInteractions(chain);
    }

    @Test void disabledProviderIsRejectedAndNonWebhookApiBypassesAdmission() throws Exception {
        var disabled = new MockHttpServletRequest("POST", "/api/v1/payments/webhooks/razorpay");
        var rejectedChain = mock(FilterChain.class);
        var rejected = new MockHttpServletResponse();
        filter(1024, 10).doFilter(disabled, rejected, rejectedChain);
        assertThat(rejected.getStatus()).isEqualTo(404);
        var query = new MockHttpServletRequest("GET", "/api/v1/payments/orders/example");
        var acceptedChain = mock(FilterChain.class);
        var accepted = new MockHttpServletResponse();
        filter(1024, 10).doFilter(query, accepted, acceptedChain);
        verify(acceptedChain).doFilter(query, accepted);
        verifyNoInteractions(jdbc, rejectedChain);
    }

    private String signature(byte[] body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_fixture".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
