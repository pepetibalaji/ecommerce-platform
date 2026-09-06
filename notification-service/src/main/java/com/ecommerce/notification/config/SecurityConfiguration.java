package com.ecommerce.notification.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  public SecurityFilterChain notificationSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }

  /**
   * Accepts the Auth Service's migration-safe scalar {@code role} claim as well as its RBAC
   * {@code roles} and {@code permissions} arrays. The scalar role keeps existing clients working
   * while multi-role access is adopted.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(this::authorities);
    return converter;
  }

  private Collection<GrantedAuthority> authorities(Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    addAuthorities(authorities, jwt.getClaim("roles"), "ROLE_");
    addAuthorities(authorities, jwt.getClaim("role"), "ROLE_");
    addAuthorities(authorities, jwt.getClaim("permissions"), "PERMISSION_");
    return authorities;
  }

  private void addAuthorities(
      Set<GrantedAuthority> authorities, Object claim, String prefix) {
    if (claim instanceof Collection<?> values) {
      values.forEach(value -> addAuthority(authorities, value, prefix));
    } else {
      addAuthority(authorities, claim, prefix);
    }
  }

  private void addAuthority(Set<GrantedAuthority> authorities, Object value, String prefix) {
    if (value == null || value.toString().isBlank()) {
      return;
    }
    String authority = value.toString().trim();
    authorities.add(
        new SimpleGrantedAuthority(authority.startsWith(prefix) ? authority : prefix + authority));
  }
}
