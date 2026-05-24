package com.ecommerce.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.security.core.userdetails.UserDetailsService;

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getServletPath();

        /*
         * Skip JWT authentication for public auth endpoints
         */
        if (requestPath.startsWith("/api/v1/auth")) {

            filterChain.doFilter(request, response);

            return;
        }

        String authHeader = request.getHeader(AUTH_HEADER);

        /*
         * No Authorization header present
         */
        if (authHeader == null ||
                !authHeader.startsWith(BEARER_PREFIX)) {

            filterChain.doFilter(request, response);

            return;
        }

        try {

            String jwt =
                    authHeader.substring(BEARER_PREFIX.length());

            String email =
                    jwtService.extractEmail(jwt);

            /*
             * Authenticate only if context is empty
             */
            if (email != null &&
                    SecurityContextHolder.getContext()
                            .getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService
                                .loadUserByUsername(email);

                /*
                 * Validate token
                 */
                if (jwtService.isTokenValid(jwt)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);
                }
            }

        } catch (Exception exception) {

            /*
             * Invalid JWT should not break request pipeline.
             * Continue filter chain and let Spring Security
             * handle authorization failure naturally.
             */
        }

        filterChain.doFilter(request, response);
    }
}