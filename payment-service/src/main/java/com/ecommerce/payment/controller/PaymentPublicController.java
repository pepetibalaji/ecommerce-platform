package com.ecommerce.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/payments")
public class PaymentPublicController {

    @GetMapping("/success")
    public ResponseEntity<String> success(
            @RequestParam String orderId,
            @RequestParam(required = false) String paymentId
    ) {
        return ResponseEntity.ok("Payment successful for order " + orderId);
    }

    @GetMapping("/cancel")
    public ResponseEntity<String> cancel(
            @RequestParam String orderId,
            @RequestParam(required = false) String paymentId
    ) {
        return ResponseEntity.ok("Payment cancelled for order " + orderId);
    }
}