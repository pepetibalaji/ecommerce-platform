package com.ecommerce.payment.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ecommerce.payment.service.*;
import com.ecommerce.payment.exception.*;

class PaymentConfirmationHttpTest {
    @Test void legacyReturnDoesNotClaimSuccessAndRedirectsToVerification() throws Exception {
        var controller = new PaymentPublicController();
        ReflectionTestUtils.setField(controller, "frontendReturnUrl", "https://pepekart.test/payment/return");
        var http = MockMvcBuilders.standaloneSetup(controller).build();
        UUID order = UUID.randomUUID();
        for (String action : new String[]{"success", "cancel"}) {
            http.perform(get("/public/payments/" + action).param("orderId", order.toString()))
                    .andExpect(status().isSeeOther())
                    .andExpect(redirectedUrl("https://pepekart.test/payment/return?orderId=" + order))
                    .andExpect(content().string(""));
        }
        http.perform(get("/public/payments/success").param("orderId", "not-an-id"))
                .andExpect(status().isBadRequest());
    }
    @Test void verifiedRefreshUsesJwtOwnerAndReturnsSafe503ForProviderOutage() throws Exception {
        var confirmations = mock(PaymentWebhookService.class);
        var http = MockMvcBuilders.standaloneSetup(new PaymentController(mock(PaymentCheckoutService.class), mock(PaymentQueryService.class), confirmations))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new PaymentConfirmationExceptionHandler()).build();
        UUID order = UUID.randomUUID(), owner = UUID.randomUUID();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "RS256").claim("userId", owner.toString()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
        try {
            when(confirmations.refreshPayment(order, owner)).thenThrow(new PaymentConfirmationUnavailableException());
            http.perform(post("/api/v1/payments/orders/" + order + "/refresh"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.message").value("Payment confirmation is temporarily unavailable. Do not pay again; retry the status check."));
            verify(confirmations).refreshPayment(order, owner);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
