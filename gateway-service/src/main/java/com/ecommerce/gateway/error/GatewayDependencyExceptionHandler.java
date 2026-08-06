package com.ecommerce.gateway.error;

import io.lettuce.core.RedisException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Converts a rate-limiter Redis outage into a useful, retryable response instead of an opaque 500.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayDependencyExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (!(exception instanceof RedisException) || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        byte[] body = "{\"type\":\"about:blank\",\"title\":\"Rate limiting unavailable\",\"status\":503,\"detail\":\"Please retry shortly.\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
