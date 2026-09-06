package com.ecommerce.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Signing key material configuration.
 *
 * <p>Production uses a keystore supplied by a secret manager, HSM, or a KMS JCA provider. The
 * private key is never stored in the application database. {@code GENERATED} is intentionally
 * limited to local/test profiles unless {@code allowEphemeral} is explicitly enabled.
 */
@ConfigurationProperties(prefix = "auth.signing-key")
public class SigningKeyProperties {

  public enum Source {
    KEY_STORE,
    GENERATED
  }

  private Source source = Source.GENERATED;
  private String keyId;
  private Resource keyStoreLocation;
  private String keyStoreType = "PKCS12";
  private String keyStoreProvider;
  private String keyStorePassword;
  private String keyAlias;
  private String keyPassword;
  private boolean allowEphemeral;

  public Source getSource() { return source; }
  public void setSource(Source source) { this.source = source == null ? Source.GENERATED : source; }
  public String getKeyId() { return keyId; }
  public void setKeyId(String keyId) { this.keyId = keyId; }
  public Resource getKeyStoreLocation() { return keyStoreLocation; }
  public void setKeyStoreLocation(Resource keyStoreLocation) { this.keyStoreLocation = keyStoreLocation; }
  public String getKeyStoreType() { return keyStoreType; }
  public void setKeyStoreType(String keyStoreType) { this.keyStoreType = keyStoreType; }
  public String getKeyStoreProvider() { return keyStoreProvider; }
  public void setKeyStoreProvider(String keyStoreProvider) { this.keyStoreProvider = keyStoreProvider; }
  public String getKeyStorePassword() { return keyStorePassword; }
  public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }
  public String getKeyAlias() { return keyAlias; }
  public void setKeyAlias(String keyAlias) { this.keyAlias = keyAlias; }
  public String getKeyPassword() { return keyPassword; }
  public void setKeyPassword(String keyPassword) { this.keyPassword = keyPassword; }
  public boolean isAllowEphemeral() { return allowEphemeral; }
  public void setAllowEphemeral(boolean allowEphemeral) { this.allowEphemeral = allowEphemeral; }
}
