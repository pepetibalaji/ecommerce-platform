package com.ecommerce.payment.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ecommerce.payment.service.*;
import com.ecommerce.payment.exception.*;
import com.ecommerce.payment.config.*;

class PaymentConfirmationHttpTest {
    @Test void legacyReturnsOnlyRedirectToApprovedFrontendWithoutClaimingSuccess() throws Exception {
        var properties = new PaymentProviderProperties();
        var env = new org.springframework.mock.env.MockEnvironment(); env.setActiveProfiles("test");
        var http = MockMvcBuilders.standaloneSetup(new PaymentPublicController(properties,
                new CheckoutUrlPolicy(properties, env))).build();
        UUID order = UUID.randomUUID(), payment = UUID.randomUUID();
        for (String action : new String[]{"success", "cancel"}) {
            http.perform(get("/public/payments/" + action).param("orderId", order.toString()).param("paymentId", payment.toString()))
                    .andExpect(status().isSeeOther())
                    .andExpect(redirectedUrl("http://localhost:5173/payment/return?orderId=" + order + "&paymentId=" + payment))
                    .andExpect(content().string(""));
        }
    }

    @Test void returnsStableSafeErrorWithRetryGuidanceAndTraceId() throws Exception {
        var confirmations = mock(PaymentWebhookService.class);
        var http = MockMvcBuilders.standaloneSetup(new PaymentController(mock(PaymentCheckoutService.class),
                mock(PaymentQueryService.class), confirmations))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new PaymentConfirmationExceptionHandler()).build();
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256")
                .claim("userId", owner.toString()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
        try {
            when(confirmations.refreshPayment(order, owner)).thenThrow(new IllegalStateException("sk_live_secret"));
            http.perform(post("/api/v1/payments/orders/" + order + "/refresh"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"))
                    .andExpect(jsonPath("$.retryable").value(true))
                    .andExpect(jsonPath("$.retryAfterSeconds").value(2))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(header().string("Retry-After", "2"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk_live_secret"))));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void paginationRejectsNegativeAndExcessiveValues() throws Exception {
        var query = mock(PaymentQueryService.class);
        var http = MockMvcBuilders.standaloneSetup(new AdminPaymentController(query))
                .setControllerAdvice(new PaymentConfirmationExceptionHandler()).build();
        http.perform(get("/api/v1/admin/payments").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_INVALID_REQUEST"));
        http.perform(get("/api/v1/admin/payments").param("size", "51"))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/admin/payments").param("page", "99999999999999999999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_INVALID_REQUEST"));
        verifyNoInteractions(query);
    }

    @Test void invalidWebhookSignatureRemainsSafeNonRetryable400() throws Exception {
        var service = mock(PaymentWebhookService.class);
        when(service.processWebhook(com.ecommerce.payment.enums.PaymentProvider.STRIPE, "{}", "invalid"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "secret provider diagnostic"));
        var http = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(service))
                .setControllerAdvice(new PaymentConfirmationExceptionHandler()).build();
        http.perform(post("/api/v1/payments/webhooks/stripe").contentType("application/json")
                        .header("Stripe-Signature", "invalid").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_WEBHOOK_INVALID"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret provider"))));
        http.perform(post("/api/v1/payments/webhooks/stripe").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_INVALID_REQUEST"));
    }

    @Test void operationsControllerAlsoUsesSafeErrorAdvice() throws Exception {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret database details"));
        var http = MockMvcBuilders.standaloneSetup(new com.ecommerce.payment.outbox.PaymentOperationsController(jdbc))
                .setControllerAdvice(new PaymentConfirmationExceptionHandler()).build();
        http.perform(get("/api/v1/admin/payments/operations/outbox"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret database"))));
    }

    @Test void customerPaymentNeverSerializesInternalFailureText() throws Exception {
        var response = new com.ecommerce.payment.dto.response.PaymentResponse();
        response.setFailureReason("sensitive provider text");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(response);
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("failureReason", "sensitive provider");
    }
}
