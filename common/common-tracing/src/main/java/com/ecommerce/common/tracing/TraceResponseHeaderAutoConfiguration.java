package com.ecommerce.common.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Adds the current Micrometer trace and span IDs to successful and error HTTP responses.
 * The configuration is activated automatically when a service includes this module.
 */
@AutoConfiguration
public class TraceResponseHeaderAutoConfiguration {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String SPAN_ID_HEADER = "X-Span-Id";

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    @ConditionalOnBean(Tracer.class)
    static class ServletTraceResponseHeaderConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 10)
        @ConditionalOnMissingBean(name = "traceIdResponseFilter")
        OncePerRequestFilter traceIdResponseFilter(Tracer tracer) {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain
                ) throws ServletException, IOException {
                    setServletTraceIdHeader(response, tracer);
                    filterChain.doFilter(request, response);
                }
            };
        }

        private static void setServletTraceIdHeader(HttpServletResponse response, Tracer tracer) {
            currentSpan(tracer).ifPresent(span -> {
                response.setHeader(TRACE_ID_HEADER, span.context().traceId());
                response.setHeader(SPAN_ID_HEADER, span.context().spanId());
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFilter.class)
    @ConditionalOnBean(Tracer.class)
    static class ReactiveTraceResponseHeaderConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 10)
        @ConditionalOnMissingBean(name = "traceIdResponseWebFilter")
        WebFilter traceIdResponseWebFilter(Tracer tracer) {
            return (exchange, chain) -> {
                exchange.getResponse().beforeCommit(() -> {
                    setTraceIdHeader(exchange.getResponse().getHeaders(), tracer);
                    return Mono.empty();
                });
                return chain.filter(exchange);
            };
        }
    }

    private static void setTraceIdHeader(org.springframework.http.HttpHeaders headers, Tracer tracer) {
        currentSpan(tracer).ifPresent(span -> {
            headers.set(TRACE_ID_HEADER, span.context().traceId());
            headers.set(SPAN_ID_HEADER, span.context().spanId());
        });
    }

    private static java.util.Optional<Span> currentSpan(Tracer tracer) {
        Span span = tracer.currentSpan();
        return span == null ? java.util.Optional.empty() : java.util.Optional.of(span);
    }
}
