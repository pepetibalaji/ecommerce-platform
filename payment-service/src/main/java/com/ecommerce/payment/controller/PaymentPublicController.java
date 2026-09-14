package com.ecommerce.payment.controller;

import com.ecommerce.payment.config.CheckoutUrlPolicy;
import com.ecommerce.payment.config.PaymentProviderProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

/** Legacy provider returns are informational redirects only; providers use frontend URLs directly. */
@Deprecated
@RestController
@RequiredArgsConstructor
@RequestMapping("/public/payments")
public class PaymentPublicController {
    private final PaymentProviderProperties properties;
    private final CheckoutUrlPolicy urls;

    @GetMapping({"/success", "/cancel"})
    public ResponseEntity<Void> redirect(@RequestParam UUID orderId, @RequestParam UUID paymentId) {
        String location = properties.getCheckout().getSuccessUrl().replace("{ORDER_ID}", orderId.toString())
                .replace("{PAYMENT_ID}", paymentId.toString());
        urls.validateReturnUrl(location);
        return ResponseEntity.status(303).header("Deprecation", "true").location(URI.create(location)).build();
    }
}
