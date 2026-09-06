package com.ecommerce.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class HashingOAuth2AuthorizationServiceTest {
  @Test
  void savesOnlyHashAndHashesRawValueForRefreshLookup() throws Exception {
    OAuth2AuthorizationService delegate = Mockito.mock(OAuth2AuthorizationService.class);
    HashingOAuth2AuthorizationService service = new HashingOAuth2AuthorizationService(delegate);
    String raw = "high-entropy-oauth-refresh-token";
    RegisteredClient client = RegisteredClient.withId("client-1").clientId("client")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://client.example.test/callback").build();
    OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
        .id("authorization-1").principalName("user@example.test")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .refreshToken(new OAuth2RefreshToken(raw, Instant.now(), Instant.now().plusSeconds(3600))).build();

    service.save(authorization);
    ArgumentCaptor<OAuth2Authorization> saved = ArgumentCaptor.forClass(OAuth2Authorization.class);
    verify(delegate).save(saved.capture());
    assertThat(saved.getValue().getRefreshToken().getToken().getTokenValue()).isEqualTo(hash(raw));
    assertThat(saved.getValue().getRefreshToken().getToken().getTokenValue()).isNotEqualTo(raw);

    service.findByToken(raw, OAuth2TokenType.REFRESH_TOKEN);
    verify(delegate).findByToken(eq(hash(raw)), eq(OAuth2TokenType.REFRESH_TOKEN));
  }

  private String hash(String value) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
