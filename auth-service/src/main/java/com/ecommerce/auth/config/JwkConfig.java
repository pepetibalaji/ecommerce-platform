package com.ecommerce.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Loads the signing key from a persistent Java {@link KeyStore}. A cloud KMS/HSM is supported
 * when its JCA provider exposes the key through a KeyStore; the private key stays provider-backed
 * and is never written to the database or application configuration.
 */
@Configuration
@EnableConfigurationProperties(SigningKeyProperties.class)
public class JwkConfig {

  @Bean
  public KeyPair keyPair(SigningKeyProperties properties, Environment environment) {
    try {
      if (properties.getSource() == SigningKeyProperties.Source.KEY_STORE) {
        return loadKeyPair(properties);
      }
      return generateDevelopmentKeyPair(properties, environment);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to load Auth signing key", exception);
    }
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource(KeyPair keyPair, SigningKeyProperties properties) {
    // The active key is the sole signing candidate; retired keys are public-only verification keys.
    // This lets Nimbus select the active key deterministically and keeps retired private material
    // out of the JWKS object graph.
    RSAKey activeKey =
        new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey(keyPair.getPrivate())
            .keyID(activeKeyId(properties))
            .build();
    List<com.nimbusds.jose.jwk.JWK> keys = new ArrayList<>();
    keys.add(activeKey);
    for (SigningKeyProperties.PreviousKey previous : properties.getPreviousKeys()) {
      RSAPublicKey previousPublicKey = loadPreviousPublicKey(previous);
      keys.add(new RSAKey.Builder(previousPublicKey)
          .keyID(requiredKeyId(previous.getKeyId())).build());
    }
    JWKSet keySet = new JWKSet(keys);
    return (selector, context) -> selector.select(keySet);
  }

  @Bean
  public JwtEncoder jwtEncoder(JWKSource<SecurityContext> source) {
    return new NimbusJwtEncoder(source);
  }

  private KeyPair loadKeyPair(SigningKeyProperties properties) throws Exception {
    if (!hasText(properties.getKeyId())) {
      throw new IllegalStateException("A stable auth.signing-key.key-id is required for a key store");
    }
    if (!hasText(properties.getKeyAlias())) {
      throw new IllegalStateException("A signing keystore alias is required");
    }
    if (properties.getKeyStoreLocation() == null && !hasText(properties.getKeyStoreProvider())) {
      throw new IllegalStateException(
          "A signing keystore location is required unless a KMS/HSM KeyStore provider is configured");
    }

    KeyStore keyStore =
        hasText(properties.getKeyStoreProvider())
            ? KeyStore.getInstance(properties.getKeyStoreType(), properties.getKeyStoreProvider())
            : KeyStore.getInstance(properties.getKeyStoreType());
    if (properties.getKeyStoreLocation() == null) {
      // Most cloud KMS/HSM KeyStore providers initialize from provider configuration and expect
      // load(null, password), rather than a local PKCS12 input stream.
      keyStore.load(null, chars(properties.getKeyStorePassword()));
    } else {
      try (InputStream input = properties.getKeyStoreLocation().getInputStream()) {
        keyStore.load(input, chars(properties.getKeyStorePassword()));
      }
    }

    Key privateKey = keyStore.getKey(properties.getKeyAlias(), chars(properties.getKeyPassword()));
    if (!(privateKey instanceof PrivateKey signingKey)
        || !(keyStore.getCertificate(properties.getKeyAlias()).getPublicKey()
            instanceof RSAPublicKey publicKey)) {
      throw new IllegalStateException("Signing key must be an RSA private key with an RSA certificate");
    }
    return new KeyPair(publicKey, signingKey);
  }

  private RSAPublicKey loadPreviousPublicKey(SigningKeyProperties.PreviousKey properties) {
    try {
      if (!hasText(properties.getKeyAlias())) {
        throw new IllegalStateException("A previous signing keystore alias is required");
      }
      KeyStore keyStore = hasText(properties.getKeyStoreProvider())
          ? KeyStore.getInstance(properties.getKeyStoreType(), properties.getKeyStoreProvider())
          : KeyStore.getInstance(properties.getKeyStoreType());
      if (properties.getKeyStoreLocation() == null) {
        keyStore.load(null, chars(properties.getKeyStorePassword()));
      } else {
        try (InputStream input = properties.getKeyStoreLocation().getInputStream()) {
          keyStore.load(input, chars(properties.getKeyStorePassword()));
        }
      }
      if (!(keyStore.getCertificate(properties.getKeyAlias()).getPublicKey() instanceof RSAPublicKey publicKey)) {
        throw new IllegalStateException("Previous signing key must have an RSA certificate");
      }
      return publicKey;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to load previous Auth signing public key", exception);
    }
  }

  private KeyPair generateDevelopmentKeyPair(
      SigningKeyProperties properties, Environment environment) throws Exception {
    if (!properties.isAllowEphemeral() && !environment.matchesProfiles("dev", "test")) {
      throw new IllegalStateException("Ephemeral signing keys are prohibited outside dev/test");
    }
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(3072);
    return generator.generateKeyPair();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String requiredKeyId(String keyId) {
    if (!hasText(keyId)) {
      throw new IllegalStateException("Every signing key requires a stable key-id");
    }
    return keyId;
  }

  private String activeKeyId(SigningKeyProperties properties) {
    // A test/dev generated key changes on every start, so it must also have a fresh kid. A
    // persistent key, however, must always have its operator-provided stable identifier.
    if (properties.getSource() == SigningKeyProperties.Source.GENERATED
        && !hasText(properties.getKeyId())) {
      return UUID.randomUUID().toString();
    }
    return requiredKeyId(properties.getKeyId());
  }

  private char[] chars(String value) {
    return value == null ? new char[0] : value.toCharArray();
  }
}
