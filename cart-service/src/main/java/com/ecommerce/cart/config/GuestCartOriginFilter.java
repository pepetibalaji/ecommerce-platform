package com.ecommerce.cart.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Origin validation protects guest-cookie writes when a deployment uses cross-site cookies. */
@Component
public class GuestCartOriginFilter extends OncePerRequestFilter {
    private final CartProperties properties;
    public GuestCartOriginFilter(CartProperties properties) { this.properties = properties; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/cart/guest") || HttpMethod.GET.matches(request.getMethod()) || HttpMethod.OPTIONS.matches(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !properties.getGuestAllowedOrigins().contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Guest cart request origin is not allowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
