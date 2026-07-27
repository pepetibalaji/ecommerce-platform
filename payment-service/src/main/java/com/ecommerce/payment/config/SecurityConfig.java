package com.ecommerce.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain paymentSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/webjars/**",

                                "/payment/v3/api-docs",
                                "/payment/v3/api-docs/**",
                                "/payment/v3/api-docs.yaml",
                                "/payment/swagger-ui.html",
                                "/payment/swagger-ui/**",
                                "/payment/swagger-resources/**",
                                "/payment/webjars/**"
                        ).permitAll()

                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**"
                        ).permitAll()

                        .requestMatchers("/api/v1/payments/webhooks/**").permitAll()
                        .requestMatchers("/public/payments/**").permitAll()
                        .requestMatchers("/api/v1/admin/payments/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/payments/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        addAuthoritiesFromClaim(authorities, jwt.getClaim("roles"));
        addAuthoritiesFromClaim(authorities, jwt.getClaim("role"));
        addAuthoritiesFromClaim(authorities, jwt.getClaim("authorities"));

        return authorities;
    }

    private void addAuthoritiesFromClaim(
            Set<GrantedAuthority> authorities,
            Object claimValue
    ) {
        if (claimValue == null) {
            return;
        }

        if (claimValue instanceof Collection<?> values) {
            values.forEach(value -> addAuthority(authorities, value));
            return;
        }

        if (claimValue instanceof String value) {
            for (String role : value.split("[,\\s]+")) {
                addAuthority(authorities, role);
            }
        }
    }

    private void addAuthority(
            Set<GrantedAuthority> authorities,
            Object rawValue
    ) {
        if (rawValue == null) {
            return;
        }

        String value = rawValue.toString().trim();

        if (value.isBlank()) {
            return;
        }

        if (value.startsWith("ROLE_")) {
            authorities.add(new SimpleGrantedAuthority(value));
            return;
        }

        authorities.add(new SimpleGrantedAuthority("ROLE_" + value));
    }
}
