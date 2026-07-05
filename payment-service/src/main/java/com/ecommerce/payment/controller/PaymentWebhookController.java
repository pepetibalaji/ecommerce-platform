package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.response.WebhookAckResponse;
import com.ecommerce.payment.enums.PaymentProvider;
import com.ecommerce.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Payment provider webhook APIs")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/stripe")
    public ResponseEntity<WebhookAckResponse> handleStripeWebhook(
            @RequestBody @NotBlank(message = "Webhook payload is required") String payload,
            @RequestHeader("Stripe-Signature")
            @NotBlank(message = "Stripe-Signature header is required") String signature
    ) {
        return ResponseEntity.ok(
                paymentWebhookService.processWebhook(
                        PaymentProvider.STRIPE,
                        payload,
                        signature
                )
        );
    }

    @PostMapping("/razorpay")
    public ResponseEntity<WebhookAckResponse> handleRazorpayWebhook(
            @RequestBody @NotBlank(message = "Webhook payload is required") String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false)
            String signature
    ) {
        return ResponseEntity.ok(
                paymentWebhookService.processWebhook(
                        PaymentProvider.RAZORPAY,
                        payload,
                        signature
                )
        );
    }
}