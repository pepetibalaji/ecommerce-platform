package com.ecommerce.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigurationTest {

  @Test
  void mapsAuthScalarAndRbacClaimsToAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("customer@example.test")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .claim("role", "CUSTOMER")
            .claim("roles", List.of("ADMIN", "CUSTOMER"))
            .claim("permissions", List.of("USER:READ"))
            .build();

    var authentication = new SecurityConfiguration().jwtAuthenticationConverter().convert(jwt);

    assertThat(authentication.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CUSTOMER", "PERMISSION_USER:READ");
  }
}
