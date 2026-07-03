package com.ecommerce.payment.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ResponseTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    public ResponseTraceFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            if (tracer != null
                    && tracer.currentSpan() != null
                    && tracer.currentSpan().context() != null) {

                String traceId = tracer.currentSpan().context().traceId();
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
        } catch (Exception ignored) {
            // Do not block the request if tracing context is unavailable.
        }

        filterChain.doFilter(request, response);
    }
}