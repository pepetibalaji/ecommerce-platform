package com.ecommerce.gateway.error;

import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisCommandExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayDependencyExceptionHandlerTest {

    private final GatewayDependencyExceptionHandler handler = new GatewayDependencyExceptionHandler();

    @Test
    void translatesRedisOutageToServiceUnavailableProblem() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me"));

        StepVerifier.create(handler.handle(exchange, new RedisConnectionException("Redis is down")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/problem+json");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Service temporarily unavailable")
                .contains("503");
    }

    @Test
    void translatesSpringDataRedisWrapperToServiceUnavailableProblem() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login"));
        RedisConnectionException cause = new RedisConnectionException("Redis is down");

        StepVerifier.create(handler.handle(exchange, new RedisConnectionFailureException("Redis is down", cause)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Service temporarily unavailable");
    }

    @Test
    void leavesRedisCommandErrorsForTheDefaultHandler() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login"));

        StepVerifier.create(handler.handle(exchange, new RedisCommandExecutionException("ERR wrongtype")))
                .expectError(RedisCommandExecutionException.class)
                .verify();
    }

    @Test
    void leavesNonRedisErrorsForTheDefaultHandler() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me"));

        StepVerifier.create(handler.handle(exchange, new IllegalStateException("unexpected")))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
