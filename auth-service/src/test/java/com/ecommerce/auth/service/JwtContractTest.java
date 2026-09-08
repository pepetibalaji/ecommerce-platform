package com.ecommerce.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.auth.config.AuthorizationServerProperties;
import com.ecommerce.auth.config.TokenCustomizerConfig;
import com.ecommerce.auth.entity.Permission;
import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.entity.enums.UserStatus;
import com.ecommerce.auth.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

/** Guards the JWT shape that the current platform resource servers consume. */
@ExtendWith(MockitoExtension.class)
class JwtContractTest {

  @Mock private JwtEncoder encoder;
  @Mock private UserRepository users;
  @Mock private AuthAuditService audit;
  @Mock private TokenBlacklistService tokenBlacklistService;

  @Test
  void directLoginTokenKeepsLegacyAndRbacClaims() {
    User user = activeUser();
    AuthorizationServerProperties properties = authorizationProperties();
    JwtTokenService service =
        new JwtTokenService(
            encoder,
            AuthorizationServerSettings.builder().issuer("https://auth.example.test").build(),
            properties,
            tokenBlacklistService);
    when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt());

    service.generateAccessToken(user);

    ArgumentCaptor<JwtEncoderParameters> parameters =
        ArgumentCaptor.forClass(JwtEncoderParameters.class);
    verify(encoder).encode(parameters.capture());
    assertClaims(parameters.getValue().getClaims(), user);
  }

  @Test
  void oauthAccessTokenHasTheSamePlatformClaimsAsDirectLogin() {
    User user = activeUser();
    AuthorizationServerProperties properties = authorizationProperties();
    when(users.findAuthorizationDataByEmailNormalized(user.getEmailNormalized()))
        .thenReturn(Optional.of(user));
    TokenCustomizerConfig config = new TokenCustomizerConfig(users, properties, audit);

    JwtEncodingContext context =
        JwtEncodingContext.with(
                JwsHeader.with(SignatureAlgorithm.RS256),
                JwtClaimsSet.builder().issuer("https://auth.example.test"))
            .registeredClient(registeredClient())
            .principal(new UsernamePasswordAuthenticationToken(user.getEmail(), "not-used"))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build();

    config.jwtTokenCustomizer().customize(context);

    assertClaims(context.getClaims().build(), user);
    verify(audit)
        .record(
            org.mockito.ArgumentMatchers.eq(user.getId()),
            org.mockito.ArgumentMatchers.eq(user.getId()),
            org.mockito.ArgumentMatchers.eq("OAUTH_ACCESS_TOKEN_ISSUED"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(java.util.Map.of("clientId", "test-client")));
  }

  private void assertClaims(JwtClaimsSet claims, User user) {
    assertThat(claims.getIssuer().toString()).isEqualTo("https://auth.example.test");
    assertThat(claims.getSubject()).isEqualTo(user.getEmail());
    Object userId = claims.getClaim("userId");
    Object role = claims.getClaim("role");
    Object roles = claims.getClaim("roles");
    Object permissions = claims.getClaim("permissions");
    Object emailVerified = claims.getClaim("email_verified");
    Object status = claims.getClaim("status");
    Object tokenVersion = claims.getClaim("tokenVersion");
    assertThat(userId).isEqualTo(user.getId().toString());
    assertThat(role).isEqualTo("ADMIN");
    assertThat(roles).isEqualTo(List.of("ADMIN", "CUSTOMER"));
    assertThat(permissions).isEqualTo(List.of("USER:READ", "USER:STATUS_WRITE"));
    assertThat(emailVerified).isEqualTo(true);
    assertThat(status).isEqualTo("ACTIVE");
    assertThat(tokenVersion).isEqualTo(3L);
    assertThat(claims.getAudience()).containsExactly("gateway-service", "payment-service");
  }

  private AuthorizationServerProperties authorizationProperties() {
    AuthorizationServerProperties properties = new AuthorizationServerProperties();
    properties.setAudiences(new LinkedHashSet<>(List.of("gateway-service", "payment-service")));
    return properties;
  }

  private User activeUser() {
    Permission read = new Permission();
    read.setCode("USER:READ");
    Permission statusWrite = new Permission();
    statusWrite.setCode("USER:STATUS_WRITE");
    Role customer = new Role();
    customer.setCode("CUSTOMER");
    Role admin = new Role();
    admin.setCode("ADMIN");
    admin.setPermissions(new LinkedHashSet<>(Set.of(read, statusWrite)));
    return User.builder()
        .id(UUID.fromString("10000000-0000-0000-0000-000000000001"))
        .name("Jane Doe")
        .email("jane@example.test")
        .emailNormalized("jane@example.test")
        .passwordHash("not-used")
        .status(UserStatus.ACTIVE)
        .emailVerifiedAt(Instant.parse("2026-01-01T00:00:00Z"))
        .tokenVersion(3L)
        .roles(new LinkedHashSet<>(Set.of(customer, admin)))
        .build();
  }

  private RegisteredClient registeredClient() {
    return RegisteredClient.withId("test-client")
        .clientId("test-client")
        .clientSecret("{noop}secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://web.example.test/login/oauth2/code/ecommerce")
        .scope("openid")
        .build();
  }

  private Jwt encodedJwt() {
    Instant now = Instant.now();
    return Jwt.withTokenValue("encoded")
        .header("alg", "RS256")
        .issuer("https://auth.example.test")
        .subject("jane@example.test")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .build();
  }
}
