package com.ecommerce.gateway.error;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Converts unhandled Redis availability failures into a useful, retryable response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayDependencyExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (!isRedisUnavailable(exception) || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        byte[] body = "{\"type\":\"about:blank\",\"title\":\"Service temporarily unavailable\",\"status\":503,\"detail\":\"A required dependency is unavailable. Please retry shortly.\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private boolean isRedisUnavailable(Throwable exception) {
        for (Throwable current = exception;
             current != null && current.getCause() != current;
             current = current.getCause()) {
            if (current instanceof RedisConnectionFailureException
                    || current instanceof RedisConnectionException
                    || current instanceof RedisCommandTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
