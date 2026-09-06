package com.ecommerce.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bootstrap clients for Spring Authorization Server.
 *
 * <p>Clients are inserted only when their {@code clientId} does not already exist. Client
 * credentials must be injected by a secret manager. A pre-hashed credential is preferred for
 * production so application startup never needs to handle a raw client secret.
 */
@Validated
@ConfigurationProperties(prefix = "auth.oauth")
public class OAuthClientProperties {

  @Valid private List<Client> clients = new ArrayList<>();

  public List<Client> getClients() {
    return clients;
  }

  public void setClients(List<Client> clients) {
    this.clients = clients == null ? new ArrayList<>() : new ArrayList<>(clients);
  }

  public static class Client {
    /** Stable database id; if omitted one is deterministically derived from {@code clientId}. */
    private String id;

    @NotBlank private String clientId;

    /** Raw secret supplied only by a secret manager. Used only when {@code clientSecretHash} is absent. */
    private String clientSecret;

    /** Delegating PasswordEncoder value, for example {bcrypt}... . Preferred for production. */
    private String clientSecretHash;

    private String clientName;
    private List<String> authenticationMethods = new ArrayList<>(List.of("client_secret_basic"));
    private List<String> grantTypes = new ArrayList<>(List.of("authorization_code", "refresh_token"));
    private List<String> redirectUris = new ArrayList<>();
    private List<String> postLogoutRedirectUris = new ArrayList<>();
    private List<String> scopes = new ArrayList<>(List.of("openid", "profile", "email"));
    private boolean requireAuthorizationConsent;
    private boolean requireProofKey = true;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private boolean reuseRefreshTokens;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getClientSecretHash() { return clientSecretHash; }
    public void setClientSecretHash(String clientSecretHash) { this.clientSecretHash = clientSecretHash; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public List<String> getAuthenticationMethods() { return authenticationMethods; }
    public void setAuthenticationMethods(List<String> authenticationMethods) {
      this.authenticationMethods = authenticationMethods == null ? new ArrayList<>() : new ArrayList<>(authenticationMethods);
    }
    public List<String> getGrantTypes() { return grantTypes; }
    public void setGrantTypes(List<String> grantTypes) {
      this.grantTypes = grantTypes == null ? new ArrayList<>() : new ArrayList<>(grantTypes);
    }
    public List<String> getRedirectUris() { return redirectUris; }
    public void setRedirectUris(List<String> redirectUris) {
      this.redirectUris = redirectUris == null ? new ArrayList<>() : new ArrayList<>(redirectUris);
    }
    public List<String> getPostLogoutRedirectUris() { return postLogoutRedirectUris; }
    public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
      this.postLogoutRedirectUris = postLogoutRedirectUris == null ? new ArrayList<>() : new ArrayList<>(postLogoutRedirectUris);
    }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes); }
    public boolean isRequireAuthorizationConsent() { return requireAuthorizationConsent; }
    public void setRequireAuthorizationConsent(boolean requireAuthorizationConsent) { this.requireAuthorizationConsent = requireAuthorizationConsent; }
    public boolean isRequireProofKey() { return requireProofKey; }
    public void setRequireProofKey(boolean requireProofKey) { this.requireProofKey = requireProofKey; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public boolean isReuseRefreshTokens() { return reuseRefreshTokens; }
    public void setReuseRefreshTokens(boolean reuseRefreshTokens) { this.reuseRefreshTokens = reuseRefreshTokens; }
  }
}
