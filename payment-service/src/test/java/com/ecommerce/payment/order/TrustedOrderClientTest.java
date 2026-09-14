package com.ecommerce.payment.order;

import com.ecommerce.common.events.security.OrderLookupSignature;
import com.ecommerce.common.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;

class TrustedOrderClientTest {
    private static final String SECRET = "test-only-order-lookup-secret-32-characters";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UUID orderId = UUID.randomUUID(), userId = UUID.randomUUID();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> mode = new AtomicReference<>("valid");
    private final AtomicBoolean requestAuthenticated = new AtomicBoolean();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private TrustedOrderClient client;
    private ObjectNode order;

    @BeforeEach void startServer() throws IOException {
        order = mapper.createObjectNode().put("schemaVersion", "1.0").put("orderId", orderId.toString())
                .put("userId", userId.toString()).put("status", "PENDING")
                .put("amount", new BigDecimal("12.50")).put("currency", "USD").putNull("paymentId")
                .put("paymentExpiresAt", Instant.now().plusSeconds(300).toString());
        body.set(order.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/payment-orders/", this::respond);
        server.start();
        client = new TrustedOrderClient(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/", SECRET);
    }

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void authenticatesExactRequestAndAcceptsMatchingSignedOrder() {
        assertThatCode(() -> client.validatePayable(orderId, userId, new BigDecimal("12.50"), "USD"))
                .doesNotThrowAnyException();
        assertThat(requestAuthenticated).isTrue();
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad-signature", "missing-signature", "tampered-body", "wrong-path", "wrong-timestamp"})
    void rejectsUnauthenticatedOrReplayBoundResponse(String responseMode) {
        mode.set(responseMode);
        assertThatThrownBy(() -> client.validatePayable(orderId, userId, new BigDecimal("12.50"), "USD"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(requestAuthenticated).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"schema", "order", "user", "amount", "missing-amount", "currency",
            "paid", "cancelled", "expired", "already-prepared", "missing-owner", "missing-order"})
    void authenticatedResponseStillMustMatchImmutablePayableOrder(String invalid) {
        switch (invalid) {
            case "schema" -> order.put("schemaVersion", "2.0");
            case "order" -> order.put("orderId", UUID.randomUUID().toString());
            case "user" -> order.put("userId", UUID.randomUUID().toString());
            case "amount" -> order.put("amount", new BigDecimal("12.51"));
            case "missing-amount" -> order.putNull("amount");
            case "currency" -> order.put("currency", "EUR");
            case "paid" -> order.put("status", "PAID");
            case "cancelled" -> order.put("status", "CANCELLED");
            case "expired" -> order.put("paymentExpiresAt", Instant.now().minusSeconds(1).toString());
            case "already-prepared" -> order.put("paymentId", UUID.randomUUID().toString());
            case "missing-owner" -> order.putNull("userId");
            case "missing-order" -> order.putNull("orderId");
            default -> throw new AssertionError(invalid);
        }
        body.set(order.toString());
        assertThatThrownBy(() -> client.validatePayable(orderId, userId, new BigDecimal("12.50"), "USD"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void cancellationPreparationRequiresExplicitDurableTombstoneOverride() {
        order.put("status", "CANCELLATION_REQUESTED");
        order.put("paymentExpiresAt", Instant.now().minusSeconds(60).toString());
        body.set(order.toString());
        assertThatThrownBy(() -> client.validatePayable(orderId, userId, new BigDecimal("12.50"), "USD"))
                .isInstanceOf(BadRequestException.class);
        assertThatCode(() -> client.validatePreparation(orderId, userId, new BigDecimal("12.50"), "USD", true))
                .doesNotThrowAnyException();
        order.put("amount", new BigDecimal("99.00"));
        body.set(order.toString());
        assertThatThrownBy(() -> client.validatePreparation(orderId, userId, new BigDecimal("12.50"), "USD", true))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void signedMalformedAndOversizeBodiesAreRejected() {
        body.set("{malformed");
        assertThatThrownBy(() -> client.lookup(orderId)).isInstanceOf(IllegalStateException.class);
        body.set("null");
        assertThatThrownBy(() -> client.lookup(orderId)).isInstanceOf(IllegalStateException.class);
        body.set(" ".repeat(16385));
        assertThatThrownBy(() -> client.lookup(orderId)).isInstanceOf(IllegalStateException.class);
    }

    @Test void missingOrderIsRejectedAndRedirectsAreNeverFollowed() {
        mode.set("missing");
        assertThatThrownBy(() -> client.lookup(orderId)).isInstanceOf(BadRequestException.class);
        mode.set("redirect");
        assertThatThrownBy(() -> client.lookup(orderId)).isInstanceOf(IllegalStateException.class);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test void signatureBindsMethodPathTimestampBodyAndSecret() {
        String request = "GET\n/internal/v1/payment-orders/" + orderId + "\n1750000000";
        String signature = OrderLookupSignature.sign(SECRET, request);
        assertThat(signature).isEqualTo(hmac(SECRET, request));
        assertThat(OrderLookupSignature.valid(SECRET, request, signature)).isTrue();
        assertThat(OrderLookupSignature.valid(SECRET, request.replace("GET", "POST"), signature)).isFalse();
        assertThat(OrderLookupSignature.valid(SECRET, request + "x", signature)).isFalse();
        assertThat(OrderLookupSignature.valid("another-test-only-secret-at-least-32", request, signature)).isFalse();
        assertThat(OrderLookupSignature.valid(SECRET, request, null)).isFalse();
        assertThat(OrderLookupSignature.valid("short", request, signature)).isFalse();
        assertThatThrownBy(() -> OrderLookupSignature.sign("short", request)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OrderLookupSignature.sign(" ".repeat(32), request)).isInstanceOf(IllegalStateException.class);
    }

    private void respond(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String timestamp = exchange.getRequestHeaders().getFirst("X-Payment-Timestamp");
        String supplied = exchange.getRequestHeaders().getFirst("X-Payment-Signature");
        boolean valid = "GET".equals(exchange.getRequestMethod())
                && path.equals("/internal/v1/payment-orders/" + orderId)
                && timestamp != null && supplied != null
                && supplied.equals(hmac(SECRET, "GET\n" + path + "\n" + timestamp));
        requestAuthenticated.set(valid);
        if (!valid) {
            exchange.sendResponseHeaders(401, -1); exchange.close(); return;
        }
        String content = body.get();
        String replyPath = "wrong-path".equals(mode.get()) ? path + "-other" : path;
        String replyTime = "wrong-timestamp".equals(mode.get()) ? "1" : timestamp;
        String responseSignature = hmac(SECRET, replyPath + "\n" + replyTime + "\n" + content);
        if ("bad-signature".equals(mode.get())) responseSignature = "0".repeat(64);
        if (!"missing-signature".equals(mode.get())) exchange.getResponseHeaders().set("X-Order-Signature", responseSignature);
        if ("tampered-body".equals(mode.get())) content += " ";
        int status = "missing".equals(mode.get()) ? 404 : "redirect".equals(mode.get()) ? 302 : 200;
        if (status == 302) exchange.getResponseHeaders().set("Location", path);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) { throw new AssertionError(impossible); }
    }
}
