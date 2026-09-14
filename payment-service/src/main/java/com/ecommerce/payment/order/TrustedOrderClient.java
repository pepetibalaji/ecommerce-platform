package com.ecommerce.payment.order;

import com.ecommerce.common.events.order.TrustedOrderSnapshot;
import com.ecommerce.common.events.security.OrderLookupSignature;
import com.ecommerce.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TrustedOrderClient {
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String secret;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public TrustedOrderClient(ObjectMapper mapper,
            @Value("${payment.order-lookup.base-url:http://localhost:8086}") String baseUrl,
            @Value("${payment.order-lookup.secret:}") String secret) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.secret = secret;
    }

    public void validatePayable(UUID orderId, UUID userId, BigDecimal amount, String currency) {
        validatePreparation(orderId, userId, amount, currency, false);
    }

    public void validatePreparation(UUID orderId, UUID userId, BigDecimal amount, String currency, boolean cancellationPending) {
        TrustedOrderSnapshot order = lookup(orderId);
        boolean pending = "PENDING".equals(order.status());
        boolean cancellation = cancellationPending && "CANCELLATION_REQUESTED".equals(order.status());
        if (!"1.0".equals(order.schemaVersion()) || !orderId.equals(order.orderId()) || !userId.equals(order.userId())
                || order.amount() == null || order.amount().compareTo(amount) != 0 || !currency.equals(order.currency())
                || (!pending && !cancellation) || order.paymentId() != null
                || (!cancellationPending && order.paymentExpiresAt() != null && !order.paymentExpiresAt().isAfter(Instant.now()))) {
            throw new BadRequestException("Order data is stale or inconsistent with payment preparation");
        }
    }

    public TrustedOrderSnapshot lookup(UUID orderId) {
        String path = "/internal/v1/payment-orders/" + orderId;
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = OrderLookupSignature.sign(secret, "GET\n" + path + "\n" + timestamp);
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5))
                    .header("X-Payment-Timestamp", timestamp).header("X-Payment-Signature", signature).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new BadRequestException("Order does not exist");
            if (response.statusCode() != 200 || response.body().length() > 16384
                    || !OrderLookupSignature.valid(secret, path + "\n" + timestamp + "\n" + response.body(),
                            response.headers().firstValue("X-Order-Signature").orElse(null))) {
                throw new IllegalStateException("Trusted Order lookup was unavailable or unauthenticated");
            }
            var snapshot = mapper.readValue(response.body(), TrustedOrderSnapshot.class);
            if (snapshot == null) throw new IllegalStateException("Trusted Order response was empty");
            return snapshot;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Trusted Order lookup interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Trusted Order lookup unavailable", exception);
        }
    }
}
