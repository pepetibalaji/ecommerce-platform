package com.ecommerce.gateway.config;

import io.micrometer.tracing.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
public class ResponseTraceFilterConfig {

    @Bean
    @Order(-1) // Forces this filter to execute extremely early, catching all routes
    public WebFilter traceIdResponseFilter(Tracer tracer) {
        return (exchange, chain) -> {
            
            // Intercept headers and inject values directly before response commits
            exchange.getResponse().beforeCommit(() -> {
                try {
                    if (tracer != null && tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
                        String traceId = tracer.currentSpan().context().traceId();
                        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
                    }
                } catch (Exception e) {
                    // Fail silently to avoid interrupting active web routing traffic
                }
                return Mono.empty();
            });

            return chain.filter(exchange);
        };
    }
}
