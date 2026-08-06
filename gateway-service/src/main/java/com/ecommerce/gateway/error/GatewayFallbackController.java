package com.ecommerce.gateway.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Provides a stable response target for Gateway CircuitBreaker route fallbacks. */
@RestController
public class GatewayFallbackController {

    @RequestMapping(value = "/__fallback/**", produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Mono<Map<String, Object>> serviceUnavailable() {
        return Mono.just(Map.of(
                "type", "about:blank",
                "title", "Upstream service unavailable",
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "detail", "Please retry shortly."
        ));
    }
}
