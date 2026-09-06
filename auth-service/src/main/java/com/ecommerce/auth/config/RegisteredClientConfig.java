package com.ecommerce.auth.config;

import com.ecommerce.auth.service.BootstrapAdminService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

/** Database-backed Spring Authorization Server state and externally configured client bootstrap. */
@Configuration
@EnableConfigurationProperties({OAuthClientProperties.class, BootstrapAdminProperties.class})
public class RegisteredClientConfig {

  @Bean
  public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
    return new JdbcRegisteredClientRepository(jdbcOperations);
  }

  @Bean
  public OAuth2AuthorizationService authorizationService(
      JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository) {
    return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
  }

  @Bean
  public OAuth2AuthorizationConsentService authorizationConsentService(
      JdbcOperations jdbcOperations, RegisteredClientRepository registeredClientRepository) {
    return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
  }

  /**
   * Inserts bootstrap clients only once. Existing clients are deliberately not overwritten during
   * startup, because modifying a client secret or redirect URI is an explicit operational change.
   */
  @Bean
  public ApplicationRunner oauthClientBootstrapper(
      OAuthClientProperties properties,
      RegisteredClientRepository registeredClientRepository,
      PasswordEncoder passwordEncoder) {
    return args -> {
      for (OAuthClientProperties.Client client : properties.getClients()) {
        if (registeredClientRepository.findByClientId(client.getClientId()) == null) {
          registeredClientRepository.save(toRegisteredClient(client, passwordEncoder));
        }
      }
    };
  }

  @Bean
  public ApplicationRunner bootstrapAdministrator(BootstrapAdminService bootstrapAdminService) {
    return args -> bootstrapAdminService.bootstrap();
  }

  private RegisteredClient toRegisteredClient(
      OAuthClientProperties.Client configuredClient, PasswordEncoder passwordEncoder) {
    validate(configuredClient);
    String id = configuredClient.getId();
    if (!StringUtils.hasText(id)) {
      id =
          UUID.nameUUIDFromBytes(
                  ("oauth-client:" + configuredClient.getClientId())
                      .getBytes(StandardCharsets.UTF_8))
              .toString();
    }

    RegisteredClient.Builder builder =
        RegisteredClient.withId(id)
            .clientId(configuredClient.getClientId())
            .clientIdIssuedAt(Instant.now())
            .clientName(
                StringUtils.hasText(configuredClient.getClientName())
                    ? configuredClient.getClientName()
                    : configuredClient.getClientId())
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(configuredClient.isRequireAuthorizationConsent())
                    .requireProofKey(configuredClient.isRequireProofKey())
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(configuredClient.getAccessTokenTtl())
                    .refreshTokenTimeToLive(configuredClient.getRefreshTokenTtl())
                    .reuseRefreshTokens(configuredClient.isReuseRefreshTokens())
                    .build());

    if (StringUtils.hasText(configuredClient.getClientSecretHash())) {
      builder.clientSecret(configuredClient.getClientSecretHash());
    } else if (StringUtils.hasText(configuredClient.getClientSecret())) {
      builder.clientSecret(passwordEncoder.encode(configuredClient.getClientSecret()));
    }

    configuredClient
        .getAuthenticationMethods()
        .forEach(method -> builder.clientAuthenticationMethod(new ClientAuthenticationMethod(method)));
    configuredClient
        .getGrantTypes()
        .forEach(grantType -> builder.authorizationGrantType(new AuthorizationGrantType(grantType)));
    configuredClient.getRedirectUris().forEach(builder::redirectUri);
    configuredClient.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
    configuredClient.getScopes().forEach(builder::scope);
    return builder.build();
  }

  private void validate(OAuthClientProperties.Client client) {
    if (!StringUtils.hasText(client.getClientId())) {
      throw new IllegalStateException("An OAuth client id is required");
    }
    boolean usesClientSecret =
        client.getAuthenticationMethods().stream()
            .anyMatch(method -> method != null && method.startsWith("client_secret"));
    if (usesClientSecret
        && !StringUtils.hasText(client.getClientSecretHash())
        && !StringUtils.hasText(client.getClientSecret())) {
      throw new IllegalStateException(
          "OAuth client " + client.getClientId() + " requires a secret or secret hash");
    }
    boolean authorizationCode =
        client.getGrantTypes().stream().anyMatch("authorization_code"::equals);
    if (authorizationCode && client.getRedirectUris().isEmpty()) {
      throw new IllegalStateException(
          "OAuth authorization-code client "
              + client.getClientId()
              + " requires at least one redirect URI");
    }
  }
}
