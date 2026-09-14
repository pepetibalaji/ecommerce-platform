package com.ecommerce.common.events.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Scoped HMAC authentication; never reuse the key for JWTs or provider webhooks. */
public final class OrderLookupSignature {
    private OrderLookupSignature() {}
    public static String sign(String secret, String message) {
        if (secret == null || secret.isBlank() || secret.length() < 32) throw new IllegalStateException("Order lookup key is not configured");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC is unavailable", exception);
        }
    }
    public static boolean valid(String secret, String message, String supplied) {
        if (supplied == null || secret == null || secret.isBlank() || secret.length() < 32) return false;
        return MessageDigest.isEqual(sign(secret, message).getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }
}
