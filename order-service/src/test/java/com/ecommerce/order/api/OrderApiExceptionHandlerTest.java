package com.ecommerce.order.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderApiExceptionHandlerTest {

    private final OrderApiExceptionHandler handler = new OrderApiExceptionHandler();

    @Test
    void exposesTheDocumentedStructuredCheckoutEnvelope() {
        MDC.put("traceId", "trace-checkout-123");
        try {
            var response = handler.handle(new OrderApiException(
                    "CHECKOUT_ITEM_INSUFFICIENT_STOCK",
                    HttpStatus.CONFLICT,
                    "One or more items are no longer available in the requested quantity.",
                    false,
                    List.of(Map.of("productId", "product-1", "requestedQuantity", 2, "availableQuantity", 1))
            ));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).containsEntry("code", "CHECKOUT_ITEM_INSUFFICIENT_STOCK")
                    .containsEntry("retryable", false)
                    .containsEntry("traceId", "trace-checkout-123");
            assertThat(response.getBody().get("details"))
                    .isEqualTo(List.of(Map.of("productId", "product-1", "requestedQuantity", 2, "availableQuantity", 1)));
        } finally {
            MDC.remove("traceId");
        }
    }
}
