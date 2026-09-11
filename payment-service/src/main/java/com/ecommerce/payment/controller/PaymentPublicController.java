package com.ecommerce.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/payments")
public class PaymentPublicController {

    @org.springframework.beans.factory.annotation.Value("${payment.checkout.frontend-return-url:http://localhost:5173/payment/return}")
    private String frontendReturnUrl;

    @GetMapping("/success")
    public ResponseEntity<Void> success(
            @RequestParam java.util.UUID orderId,
            @RequestParam(required = false) java.util.UUID paymentId
    ) {
        return redirect(orderId, paymentId);
    }

    @GetMapping("/cancel")
    public ResponseEntity<Void> cancel(
            @RequestParam java.util.UUID orderId,
            @RequestParam(required = false) java.util.UUID paymentId
    ) {
        return redirect(orderId, paymentId);
    }

    private ResponseEntity<Void> redirect(java.util.UUID orderId, java.util.UUID paymentId) {
        var location = org.springframework.web.util.UriComponentsBuilder.fromUriString(frontendReturnUrl)
                .queryParam("orderId", orderId);
        if (paymentId != null) location.queryParam("paymentId", paymentId);
        return ResponseEntity.status(303).location(location.build().toUri()).build();
    }
}
