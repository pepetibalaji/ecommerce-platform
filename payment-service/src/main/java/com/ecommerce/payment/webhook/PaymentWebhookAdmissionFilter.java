package com.ecommerce.payment.webhook;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Caps the raw body before Spring materializes it, preserving the exact bytes for signature checks. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PaymentWebhookAdmissionFilter extends OncePerRequestFilter {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private final JdbcTemplate jdbc;
    @Value("${payment.webhook.max-body-bytes:262144}") private int maxBodyBytes;
    @Value("${payment.webhook.stripe-requests-per-minute:600}") private int stripeRate;
    public PaymentWebhookAdmissionFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/payments/webhooks/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!request.getRequestURI().equals("/api/v1/payments/webhooks/stripe")) {
            reject(response, 404, "PAYMENT_PROVIDER_CONFIGURATION_ERROR"); return;
        }
        if (request.getContentLengthLong() > maxBodyBytes) {
            reject(response, 413, "WEBHOOK_PAYLOAD_TOO_LARGE"); return;
        }
        Integer count;
        try {
            count = jdbc.queryForObject("""
                    INSERT INTO payment_webhook_rate_limit(provider,window_start,request_count)
                    VALUES('STRIPE',date_trunc('minute',now()),1)
                    ON CONFLICT(provider) DO UPDATE SET
                        request_count=CASE WHEN payment_webhook_rate_limit.window_start=EXCLUDED.window_start
                            THEN payment_webhook_rate_limit.request_count+1 ELSE 1 END,
                        window_start=EXCLUDED.window_start RETURNING request_count
                    """, Integer.class);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            // Failing open would remove the shared limit precisely when infrastructure is unhealthy.
            logger.warn("payment_webhook_admission_unavailable errorType=" + unavailable.getClass().getSimpleName());
            reject(response, 503, "WEBHOOK_ADMISSION_UNAVAILABLE"); return;
        }
        if (count == null) { reject(response, 503, "WEBHOOK_ADMISSION_UNAVAILABLE"); return; }
        if (count > stripeRate) { reject(response, 429, "WEBHOOK_RATE_LIMITED"); return; }
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) { reject(response, 413, "WEBHOOK_PAYLOAD_TOO_LARGE"); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Synchronous webhook input");
                    }
                };
            }
            @Override public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        Integer retryAfter = null;
        if (status == 429) retryAfter = 60;
        else if (status == 503) retryAfter = 2;
        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) traceId = java.util.UUID.randomUUID().toString();
        var error = new java.util.LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", "Webhook request rejected.");
        error.put("retryable", retryAfter != null);
        error.put("retryAfterSeconds", retryAfter);
        error.put("traceId", traceId);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (retryAfter != null) response.setHeader("Retry-After", retryAfter.toString());
        response.getWriter().write(JSON.writeValueAsString(error));
    }
}
