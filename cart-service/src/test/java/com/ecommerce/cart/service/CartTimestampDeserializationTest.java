package com.ecommerce.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.cart.model.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CartTimestampDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void readsLegacyOffsetlessTimestampAsUtc() {
        Cart cart = readCart("2026-09-10T13:19:09.5504947");

        assertThat(cart.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-10T13:19:09.550494700Z"));
    }

    @Test
    void readsCurrentIsoInstant() {
        Cart cart = readCart("2026-09-10T13:19:09.550494700Z");

        assertThat(cart.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-10T13:19:09.550494700Z"));
    }

    private Cart readCart(String updatedAt) {
        return objectMapper.convertValue(Map.of(
                "userId", "customer-1",
                "items", List.of(),
                "updatedAt", updatedAt,
                "version", 1
        ), Cart.class);
    }
}
