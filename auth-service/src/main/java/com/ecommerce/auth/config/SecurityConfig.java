package com.ecommerce.auth.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  @Order(2)
  public SecurityFilterChain appSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

    http.securityMatcher("/api/v1/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/verification/confirm",
                        "/api/v1/auth/verification/resend",
                        "/api/v1/auth/password/forgot",
                        "/api/v1/auth/password/reset",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/users/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

  /**
   * The delivery-token endpoint performs constant-time shared-secret verification in
   * {@code InternalServiceAuthorizer}; it must not be exposed through the public API chain.
   */
  @Bean
  @Order(3)
  public SecurityFilterChain internalServiceSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/internal/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  /**
   * OAuth authorization-code requests redirect here for the authenticated resource owner. This
   * chain also owns non-API operational and documentation paths, which are intentionally outside
   * the stateless API matcher above.
   */
  @Bean
  @Order(4)
  public SecurityFilterChain browserAndOperationsSecurityFilterChain(HttpSecurity http)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/login",
                        "/error",
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(Customizer.withDefaults());
    return http.build();
  }

  /**
   * Maps Auth-issued custom claims to Spring Security authorities. The scalar {@code role} claim
   * remains supported for consumers during the RBAC migration; {@code roles} and
   * {@code permissions} are the authoritative multi-value claims.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Set<GrantedAuthority> authorities = new LinkedHashSet<>();
          addAuthorities(jwt, "roles", "ROLE_", authorities, false);
          if (authorities.stream().noneMatch(authority -> authority.getAuthority().startsWith("ROLE_"))) {
            addAuthorities(jwt, "role", "ROLE_", authorities, false);
          }
          addAuthorities(jwt, "permissions", "PERMISSION_", authorities, false);
          addAuthorities(jwt, "scope", "SCOPE_", authorities, true);
          return authorities;
        });
    return converter;
  }

  private void addAuthorities(
      Jwt jwt,
      String claimName,
      String authorityPrefix,
      Collection<GrantedAuthority> target,
      boolean splitWhitespace) {
    Object claim = jwt.getClaim(claimName);
    if (claim instanceof Collection<?> values) {
      values.forEach(value -> addAuthority(value, authorityPrefix, target));
    } else if (claim instanceof String value) {
      if (splitWhitespace) {
        for (String part : value.split("\\s+")) {
          addAuthority(part, authorityPrefix, target);
        }
      } else {
        addAuthority(value, authorityPrefix, target);
      }
    }
  }

  private void addAuthority(Object value, String authorityPrefix, Collection<GrantedAuthority> target) {
    if (value == null) {
      return;
    }
    String authority = value.toString().trim();
    if (!authority.isEmpty()) {
      target.add(new SimpleGrantedAuthority(authorityPrefix + authority));
    }
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }
}
