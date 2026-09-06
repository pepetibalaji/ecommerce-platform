package com.ecommerce.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwkConfigTest {

  @Test
  void generatedDevelopmentKeyUsesConfiguredStableKidInJwtHeader() throws Exception {
    SigningKeyProperties properties = new SigningKeyProperties();
    properties.setSource(SigningKeyProperties.Source.GENERATED);
    properties.setAllowEphemeral(true);
    properties.setKeyId("auth-dev-key-2026-01");
    StandardEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles("dev");
    JwkConfig config = new JwkConfig();

    JwtEncoder encoder = config.jwtEncoder(config.jwkSource(config.keyPair(properties, environment), properties));
    String token = encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
        .issuer("https://auth.example.test").subject("user@example.test").issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60)).build())).getTokenValue();

    assertThat(SignedJWT.parse(token).getHeader().getKeyID()).isEqualTo("auth-dev-key-2026-01");
  }
}
