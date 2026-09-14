package com.ecommerce.order.controller;

import com.ecommerce.common.events.order.TrustedOrderSnapshot;
import com.ecommerce.common.events.security.OrderLookupSignature;
import com.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/payment-orders")
public class InternalPaymentOrderController {
    private final OrderRepository orders;
    private final ObjectMapper mapper;
    @Value("${order.payment-lookup.secret:}") private String secret;

    @GetMapping(value = "/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> get(@PathVariable UUID orderId,
            @RequestHeader(value="X-Payment-Timestamp", required=false) String timestamp,
            @RequestHeader(value="X-Payment-Signature", required=false) String signature) throws Exception {
        String path = "/internal/v1/payment-orders/" + orderId;
        long requestSeconds;
        try { requestSeconds = Long.parseLong(timestamp); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        long now = Instant.now().getEpochSecond();
        if (requestSeconds < now - 60 || requestSeconds > now + 60
                || !OrderLookupSignature.valid(secret, "GET\n" + path + "\n" + timestamp, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var order = orders.findById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var snapshot = new TrustedOrderSnapshot("1.0", order.getId(), order.getUserId(), order.getStatus().name(),
                order.getTotalAmount(), order.getCurrency(), order.getPaymentId(), order.getPaymentExpiresAt());
        String body = mapper.writeValueAsString(snapshot);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("X-Order-Signature", OrderLookupSignature.sign(secret, path + "\n" + timestamp + "\n" + body))
                .contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
