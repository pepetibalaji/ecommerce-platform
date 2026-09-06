package com.ecommerce.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Produces deterministic, opaque action tokens from an unguessable action id and a secret-manager
 * supplied HMAC key. The database stores only a SHA-256 verifier, never the token itself.
 */
@Component
public class ActionTokenCodec {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private final byte[] signingKey;

  public ActionTokenCodec(
      @Value("${auth.action-token.signing-secret:}") String configuredSecret,
      Environment environment) {
    byte[] configured =
        configuredSecret == null ? new byte[0] : configuredSecret.getBytes(StandardCharsets.UTF_8);
    if (configured.length == 0) {
      if (!environment.matchesProfiles("dev", "test")) {
        throw new IllegalStateException(
            "auth.action-token.signing-secret must be supplied outside dev/test");
      }
      configured = new byte[32];
      new SecureRandom().nextBytes(configured);
    }
    if (configured.length < 32) {
      throw new IllegalStateException("auth.action-token.signing-secret must be at least 32 bytes");
    }
    this.signingKey = configured.clone();
  }

  /** Same action id always produces the same delivery token, making Notification retries safe. */
  public String tokenFor(UUID actionId) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(actionId.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to create action token", exception);
    }
  }

  public String hash(String rawToken) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to hash action token", exception);
    }
  }
}
