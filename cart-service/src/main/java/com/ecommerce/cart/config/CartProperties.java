package com.ecommerce.cart.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis expiry settings for customer and anonymous carts.
 *
 * <p>Values are supplied by Config Server in deployed environments and retain
 * safe local defaults when Config Server is unavailable.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "cart")
public class CartProperties {

    @Valid
    @NotNull
    private Expiry customer = new Expiry(Duration.ofDays(7));

    @Valid
    @NotNull
    private Guest guest = new Guest();

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @Valid
    @NotNull
    private Idempotency idempotency = new Idempotency();

    private List<String> guestAllowedOrigins = List.of("http://localhost:3000", "http://localhost:5173");

    @Getter
    @Setter
    public static class Idempotency {
        @NotNull
        private Duration ttl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Limits {
        @jakarta.validation.constraints.Min(1)
        private int maxQuantityPerItem = 100;
        @jakarta.validation.constraints.Min(1)
        private int maxLines = 100;
    }

    @Getter
    @Setter
    public static class Expiry {

        @NotNull
        private Duration ttl;

        public Expiry() {
            this(Duration.ofDays(7));
        }

        private Expiry(Duration ttl) {
            this.ttl = ttl;
        }
    }

    @Getter
    @Setter
    public static class Guest {
        @NotNull
        private Duration ttl = Duration.ofDays(30);

        @NotNull
        private Cookie cookie = new Cookie();
    }

    @Getter
    @Setter
    public static class Cookie {
        private String name = "guestId";
        private String sameSite = "Lax";
        private boolean secure;
        private String domain;
    }
}
